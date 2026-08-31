// SPDX-License-Identifier: MIT
/*
 * Bounded by the caller with timeout(1), this diagnostic proves that the
 * pinned RDK X5 Vivante userspace can create a hardware EGL/GLES2 context and
 * that the Nano2D userspace can open its matching kernel device.  It loads the
 * runtime ABIs dynamically.  It is compiled with the selected headers from
 * the matching x5-hobot-multimedia-dev release, without requiring linker
 * symlinks or promoting the private runtime to a global Yocto provider.
 */

#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <gbm.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#if defined(__GNUC__)
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wpedantic"
#endif
#include <GC820/nano2D.h>
#if defined(__GNUC__)
#pragma GCC diagnostic pop
#endif

typedef struct gbm_device *(*gbm_create_device_fn)(int fd);
typedef void (*gbm_device_destroy_fn)(struct gbm_device *device);

typedef const GLubyte *(*gl_get_string_fn)(GLenum name);
typedef void (*gl_clear_color_fn)(GLfloat red, GLfloat green, GLfloat blue,
                                  GLfloat alpha);
typedef void (*gl_clear_fn)(GLbitfield mask);
typedef void (*gl_finish_fn)(void);

typedef n2d_error_t (*n2d_open_fn)(n2d_void);
typedef n2d_error_t (*n2d_close_fn)(n2d_void);

struct egl_api {
    PFNEGLGETDISPLAYPROC get_display;
    PFNEGLINITIALIZEPROC initialize;
    PFNEGLTERMINATEPROC terminate;
    PFNEGLQUERYSTRINGPROC query_string;
    PFNEGLGETPROCADDRESSPROC get_proc_address;
    PFNEGLGETERRORPROC get_error;
    PFNEGLCHOOSECONFIGPROC choose_config;
    PFNEGLBINDAPIPROC bind_api;
    PFNEGLCREATECONTEXTPROC create_context;
    PFNEGLDESTROYCONTEXTPROC destroy_context;
    PFNEGLCREATEPBUFFERSURFACEPROC create_pbuffer_surface;
    PFNEGLDESTROYSURFACEPROC destroy_surface;
    PFNEGLMAKECURRENTPROC make_current;
};

static int load_symbol(void *handle, const char *name, void *destination,
                       size_t destination_size)
{
    void *symbol;
    const char *error;

    if (destination_size != sizeof(symbol)) {
        fprintf(stderr, "unsupported function-pointer size for %s\n", name);
        return -1;
    }

    dlerror();
    symbol = dlsym(handle, name);
    error = dlerror();
    if (error != NULL) {
        fprintf(stderr, "missing symbol %s: %s\n", name, error);
        return -1;
    }

    memcpy(destination, &symbol, sizeof(symbol));
    return 0;
}

#define LOAD_REQUIRED(handle, api, member, symbol_name)                         \
    do {                                                                        \
        if (load_symbol((handle), (symbol_name), &(api).member,                 \
                        sizeof((api).member)) != 0)                             \
            goto out;                                                           \
    } while (0)

static int open_drm_device(const char *requested, const char **selected)
{
    static const char *const candidates[] = {
        "/dev/dri/renderD128",
        "/dev/dri/card0",
        NULL,
    };
    const char *const *candidate;
    int fd;

    if (requested != NULL) {
        fd = open(requested, O_RDWR | O_CLOEXEC);
        if (fd < 0)
            fprintf(stderr, "cannot open requested DRM device %s: %s\n",
                    requested, strerror(errno));
        else
            *selected = requested;
        return fd;
    }

    for (candidate = candidates; *candidate != NULL; ++candidate) {
        fd = open(*candidate, O_RDWR | O_CLOEXEC);
        if (fd >= 0) {
            *selected = *candidate;
            return fd;
        }
    }

    return -1;
}

static int has_software_renderer(const char *renderer)
{
    return renderer == NULL || strstr(renderer, "llvmpipe") != NULL ||
           strstr(renderer, "softpipe") != NULL ||
           strstr(renderer, "Software Rasterizer") != NULL;
}

static const char *printable(const char *value)
{
    return value != NULL ? value : "(null)";
}

static int test_nano2d(void)
{
    void *library = NULL;
    n2d_open_fn n2d_open = NULL;
    n2d_close_fn n2d_close = NULL;
    struct stat device_status;
    int opened = 0;
    int result = -1;
    n2d_error_t status;

    if (stat("/dev/nano2d", &device_status) != 0 ||
        !S_ISCHR(device_status.st_mode)) {
        fprintf(stderr, "Nano2D character device /dev/nano2d is unavailable\n");
        goto out;
    }

    library = dlopen("libNano2D.so", RTLD_NOW | RTLD_LOCAL);
    if (library == NULL) {
        fprintf(stderr, "cannot load libNano2D.so: %s\n", dlerror());
        goto out;
    }
    if (load_symbol(library, "n2d_open", &n2d_open, sizeof(n2d_open)) != 0 ||
        load_symbol(library, "n2d_close", &n2d_close, sizeof(n2d_close)) != 0)
        goto out;

    status = n2d_open();
    if (status != N2D_SUCCESS) {
        fprintf(stderr, "n2d_open failed with status %d\n", (int)status);
        goto out;
    }
    opened = 1;

    status = n2d_close();
    opened = 0;
    if (status != N2D_SUCCESS) {
        fprintf(stderr, "n2d_close failed with status %d\n", (int)status);
        goto out;
    }

    puts("nano2d=hardware-open-pass");
    result = 0;

out:
    if (opened && n2d_close != NULL)
        (void)n2d_close();
    if (library != NULL)
        dlclose(library);
    return result;
}

int main(int argc, char **argv)
{
    static const EGLint config_attributes[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE,
    };
    static const EGLint context_attributes[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE,
    };
    static const EGLint surface_attributes[] = {
        EGL_WIDTH, 1,
        EGL_HEIGHT, 1,
        EGL_NONE,
    };
    struct egl_api egl = {0};
    void *egl_library = NULL;
    void *gles_library = NULL;
    void *gbm_library = NULL;
    struct gbm_device *gbm_device = NULL;
    gbm_create_device_fn gbm_create_device = NULL;
    gbm_device_destroy_fn gbm_device_destroy = NULL;
    PFNEGLGETPLATFORMDISPLAYEXTPROC get_platform_display = NULL;
    gl_get_string_fn gl_get_string = NULL;
    gl_clear_color_fn gl_clear_color = NULL;
    gl_clear_fn gl_clear = NULL;
    gl_finish_fn gl_finish = NULL;
    EGLDisplay display = NULL;
    EGLConfig config = NULL;
    EGLContext context = NULL;
    EGLSurface surface = NULL;
    EGLint major = 0;
    EGLint minor = 0;
    EGLint config_count = 0;
    const char *requested_device = NULL;
    const char *selected_device = NULL;
    const char *renderer;
    const char *vendor;
    const char *gl_version;
    int display_initialized = 0;
    int drm_fd = -1;
    int result = EXIT_FAILURE;

    if (argc > 2) {
        fprintf(stderr, "usage: %s [DRM_DEVICE]\n", argv[0]);
        return EXIT_FAILURE;
    }
    if (argc == 2)
        requested_device = argv[1];

    /*
     * RDKOS selects the Vivante window-system backend through this vendor
     * variable.  Its default is X11 and dereferences a missing X display on a
     * headless image, even when eglGetPlatformDisplayEXT receives a GBM
     * device.  Preserve an explicit caller override, otherwise select the
     * hardware GBM backend used by this diagnostic.
     */
    if (getenv("VIV_EGL_PLATFORM") == NULL &&
        setenv("VIV_EGL_PLATFORM", "gbm", 0) != 0) {
        fprintf(stderr, "cannot select Vivante GBM platform: %s\n",
                strerror(errno));
        goto out;
    }

    egl_library = dlopen("libEGL.so.1", RTLD_NOW | RTLD_LOCAL);
    if (egl_library == NULL) {
        fprintf(stderr, "cannot load libEGL.so.1: %s\n", dlerror());
        goto out;
    }

    LOAD_REQUIRED(egl_library, egl, get_display, "eglGetDisplay");
    LOAD_REQUIRED(egl_library, egl, initialize, "eglInitialize");
    LOAD_REQUIRED(egl_library, egl, terminate, "eglTerminate");
    LOAD_REQUIRED(egl_library, egl, query_string, "eglQueryString");
    LOAD_REQUIRED(egl_library, egl, get_proc_address, "eglGetProcAddress");
    LOAD_REQUIRED(egl_library, egl, get_error, "eglGetError");
    LOAD_REQUIRED(egl_library, egl, choose_config, "eglChooseConfig");
    LOAD_REQUIRED(egl_library, egl, bind_api, "eglBindAPI");
    LOAD_REQUIRED(egl_library, egl, create_context, "eglCreateContext");
    LOAD_REQUIRED(egl_library, egl, destroy_context, "eglDestroyContext");
    LOAD_REQUIRED(egl_library, egl, create_pbuffer_surface,
                  "eglCreatePbufferSurface");
    LOAD_REQUIRED(egl_library, egl, destroy_surface, "eglDestroySurface");
    LOAD_REQUIRED(egl_library, egl, make_current, "eglMakeCurrent");

    drm_fd = open_drm_device(requested_device, &selected_device);
    if (requested_device != NULL && drm_fd < 0)
        goto out;

    if (drm_fd >= 0) {
        gbm_library = dlopen("libgbm.so.1", RTLD_NOW | RTLD_LOCAL);
        if (gbm_library != NULL &&
            load_symbol(gbm_library, "gbm_create_device", &gbm_create_device,
                        sizeof(gbm_create_device)) == 0 &&
            load_symbol(gbm_library, "gbm_device_destroy", &gbm_device_destroy,
                        sizeof(gbm_device_destroy)) == 0) {
            gbm_device = gbm_create_device(drm_fd);
        }

        if (gbm_device != NULL) {
            __eglMustCastToProperFunctionPointerType platform_symbol =
                egl.get_proc_address("eglGetPlatformDisplayEXT");
            if (platform_symbol != NULL)
                memcpy(&get_platform_display, &platform_symbol,
                       sizeof(get_platform_display));
            if (get_platform_display != NULL)
                display = get_platform_display(EGL_PLATFORM_GBM_KHR,
                                               gbm_device, NULL);
            else
                display = egl.get_display((EGLNativeDisplayType)gbm_device);
        }
    }

    if (display == NULL && requested_device == NULL) {
        selected_device = "default";
        display = egl.get_display(EGL_DEFAULT_DISPLAY);
    }
    if (display == EGL_NO_DISPLAY) {
        fprintf(stderr, "eglGetDisplay failed: EGL error 0x%04x\n",
                (unsigned int)egl.get_error());
        goto out;
    }

    if (egl.initialize(display, &major, &minor) == EGL_FALSE) {
        fprintf(stderr, "eglInitialize failed: EGL error 0x%04x\n",
                (unsigned int)egl.get_error());
        goto out;
    }
    display_initialized = 1;

    if (egl.choose_config(display, config_attributes, &config, 1,
                          &config_count) == EGL_FALSE || config_count < 1) {
        fprintf(stderr, "eglChooseConfig failed: EGL error 0x%04x\n",
                (unsigned int)egl.get_error());
        goto out;
    }
    if (egl.bind_api(EGL_OPENGL_ES_API) == EGL_FALSE) {
        fprintf(stderr, "eglBindAPI failed: EGL error 0x%04x\n",
                (unsigned int)egl.get_error());
        goto out;
    }

    context = egl.create_context(display, config, EGL_NO_CONTEXT,
                                 context_attributes);
    if (context == EGL_NO_CONTEXT) {
        fprintf(stderr, "eglCreateContext failed: EGL error 0x%04x\n",
                (unsigned int)egl.get_error());
        goto out;
    }
    surface = egl.create_pbuffer_surface(display, config, surface_attributes);
    if (surface == EGL_NO_SURFACE) {
        fprintf(stderr, "eglCreatePbufferSurface failed: EGL error 0x%04x\n",
                (unsigned int)egl.get_error());
        goto out;
    }
    if (egl.make_current(display, surface, surface, context) == EGL_FALSE) {
        fprintf(stderr, "eglMakeCurrent failed: EGL error 0x%04x\n",
                (unsigned int)egl.get_error());
        goto out;
    }

    gles_library = dlopen("libGLESv2.so.2", RTLD_NOW | RTLD_LOCAL);
    if (gles_library == NULL) {
        fprintf(stderr, "cannot load libGLESv2.so.2: %s\n", dlerror());
        goto out;
    }
    if (load_symbol(gles_library, "glGetString", &gl_get_string,
                    sizeof(gl_get_string)) != 0 ||
        load_symbol(gles_library, "glClearColor", &gl_clear_color,
                    sizeof(gl_clear_color)) != 0 ||
        load_symbol(gles_library, "glClear", &gl_clear,
                    sizeof(gl_clear)) != 0 ||
        load_symbol(gles_library, "glFinish", &gl_finish,
                    sizeof(gl_finish)) != 0)
        goto out;

    renderer = (const char *)gl_get_string(GL_RENDERER);
    vendor = (const char *)gl_get_string(GL_VENDOR);
    gl_version = (const char *)gl_get_string(GL_VERSION);
    if (has_software_renderer(renderer)) {
        fprintf(stderr, "software or missing GLES renderer: %s\n",
                printable(renderer));
        goto out;
    }

    gl_clear_color(0.0f, 0.0f, 0.0f, 1.0f);
    gl_clear(GL_COLOR_BUFFER_BIT);
    gl_finish();

    printf("drm_device=%s\n", selected_device);
    printf("egl_version=%d.%d\n", major, minor);
    printf("egl_vendor=%s\n", printable(egl.query_string(display, EGL_VENDOR)));
    printf("egl_runtime=%s\n", printable(egl.query_string(display, EGL_VERSION)));
    printf("gles_vendor=%s\n", printable(vendor));
    printf("gles_renderer=%s\n", renderer);
    printf("gles_version=%s\n", printable(gl_version));
    fflush(stdout);

    if (test_nano2d() != 0)
        goto out;

    puts("RDK_X5_GPU_SMOKE_PASS");
    result = EXIT_SUCCESS;

out:
    if (display_initialized)
        (void)egl.make_current(display, EGL_NO_SURFACE, EGL_NO_SURFACE,
                               EGL_NO_CONTEXT);
    if (surface != EGL_NO_SURFACE)
        (void)egl.destroy_surface(display, surface);
    if (context != EGL_NO_CONTEXT)
        (void)egl.destroy_context(display, context);
    if (display_initialized)
        (void)egl.terminate(display);
    if (gles_library != NULL)
        dlclose(gles_library);
    if (gbm_device != NULL && gbm_device_destroy != NULL)
        gbm_device_destroy(gbm_device);
    if (gbm_library != NULL)
        dlclose(gbm_library);
    if (drm_fd >= 0)
        close(drm_fd);
    if (egl_library != NULL)
        dlclose(egl_library);
    return result;
}
