/*
  Simple DirectMedia Layer
  Copyright (C) 1997-2024 Sam Lantinga <slouken@libsdl.org>

  This software is provided 'as-is', without any express or implied
  warranty.  In no event will the authors be held liable for any damages
  arising from the use of this software.

  Permission is granted to anyone to use this software for any purpose,
  including commercial applications, and to alter it and redistribute it
  freely, subject to the following restrictions:

  1. The origin of this software must not be misrepresented; you must not
     claim that you wrote the original software. If you use this software
     in a product, an acknowledgment in the product documentation would be
     appreciated but is not required.
  2. Altered source versions must be plainly marked as such, and must not be
     misrepresented as being the original software.
  3. This notice may not be removed or altered from any source distribution.
*/

#include "../../SDL_internal.h"

#ifndef SDL_androidgl4es_h_
#define SDL_androidgl4es_h_

#if SDL_VIDEO_DRIVER_ANDROID && defined(SDL_VIDEO_OPENGL) && defined(SDL_VIDEO_OPENGL_GL4ES)

#include "../SDL_sysvideo.h"
#include "../SDL_egl_c.h"

/* OpenGL functions for Android using gl4es */
extern int Android_GL4ES_LoadLibrary(_THIS, const char* path);
extern void* Android_GL4ES_GetProcAddress(_THIS, const char* proc);
extern void Android_GL4ES_UnloadLibrary(_THIS);
extern SDL_GLContext Android_GL4ES_CreateContext(_THIS, SDL_Window* window);
extern int Android_GL4ES_MakeCurrent(_THIS, SDL_Window* window, SDL_GLContext context);
extern int Android_GL4ES_SwapWindow(_THIS, SDL_Window* window);
extern void Android_GL4ES_DeleteContext(_THIS, SDL_GLContext context);
extern void Android_GL4ES_GetDrawableSize(_THIS, SDL_Window* window, int* w, int* h);
extern int Android_GL4ES_SetSwapInterval(_THIS, int interval);
extern int Android_GL4ES_GetSwapInterval(_THIS);

#endif /* SDL_VIDEO_DRIVER_ANDROID && SDL_VIDEO_OPENGL && SDL_VIDEO_OPENGL_GL4ES */

#endif /* SDL_androidgl4es_h_ */

