#include "game.hpp"
#include "../third_party/stb_image.h"

#include <SDL3/SDL.h>
#include <SDL3/SDL_main.h>
#include <algorithm>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

namespace {

static std::string dirnameOf(const char* path) {
    if (!path) return "";
    std::string s(path);
    while (!s.empty() && (s.back() == '/' || s.back() == '\\')) s.pop_back();
    return s;
}

static bool isDirWritable(const std::string& dir) {
    if (dir.empty()) return false;
    std::string testPath = dir + "/.ge_write_test";
    std::ofstream f(testPath, std::ios::trunc);
    if (!f.is_open()) return false;
    f.close();
    std::remove(testPath.c_str());
    return true;
}

static void applyWindowIcon(SDL_Window* window, const PakReader& pak) {
    std::vector<uint8_t> raw;
    if (!pak.readFile("icon.png", raw) || raw.empty()) return;
    int w = 0, h = 0, ch = 0;
    uint8_t* pixels = stbi_load_from_memory(raw.data(), static_cast<int>(raw.size()), &w, &h, &ch, 4);
    if (!pixels || w <= 0 || h <= 0) {
        if (pixels) stbi_image_free(pixels);
        return;
    }
    SDL_Surface* surf = SDL_CreateSurfaceFrom(w, h, SDL_PIXELFORMAT_RGBA32, pixels, w * 4);
    if (surf) {
        SDL_SetWindowIcon(window, surf);
        SDL_DestroySurface(surf);
    }
    stbi_image_free(pixels);
}

static void computeInitialWindowSize(int vw, int vh, int& outW, int& outH) {
    int scale = (vw <= 640 && vh <= 640) ? 2 : 1;
    outW = vw * scale;
    outH = vh * scale;
    SDL_DisplayID disp = SDL_GetPrimaryDisplay();
    SDL_Rect usable{0, 0, 1280, 720};
    if (disp && SDL_GetDisplayUsableBounds(disp, &usable)) {
        int maxW = std::max(320, usable.w - 80);
        int maxH = std::max(240, usable.h - 100);
        if (outW > maxW || outH > maxH) {
            float k = std::min(static_cast<float>(maxW) / outW, static_cast<float>(maxH) / outH);
            outW = std::max(240, static_cast<int>(outW * k));
            outH = std::max(240, static_cast<int>(outH * k));
        }
    }
}

} // namespace

int main(int argc, char* argv[]) {
    std::string pakPath;
    std::string dirPath;
    for (int i = 1; i < argc; ++i) {
        if (std::strcmp(argv[i], "--pak") == 0 && i + 1 < argc) {
            pakPath = argv[++i];
        } else if (std::strcmp(argv[i], "--dir") == 0 && i + 1 < argc) {
            dirPath = argv[++i];
        }
    }

    if (!SDL_Init(SDL_INIT_VIDEO | SDL_INIT_AUDIO)) {
        SDL_Log("SDL_Init error: %s", SDL_GetError());
        return 1;
    }

    const char* baseRaw = SDL_GetBasePath();
    std::string baseDir = dirnameOf(baseRaw);

    if (pakPath.empty() && dirPath.empty()) {
        std::string candidate = baseDir.empty() ? "juego.pak" : (baseDir + "/juego.pak");
        std::ifstream pf(candidate, std::ios::binary);
        if (pf.good()) {
            pakPath = candidate;
        } else if (!baseDir.empty()) {
            std::string jsonCand = baseDir + "/game.json";
            std::ifstream jf(jsonCand, std::ios::binary);
            if (jf.good()) dirPath = baseDir;
        }
        if (pakPath.empty() && dirPath.empty()) {
            pakPath = "juego.pak";
        }
    }

    Game game;
    if (!game.loadFromPakOrDir(pakPath, dirPath)) {
        SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, "Error",
                                 "No se pudo abrir juego.pak ni game.json.", nullptr);
        SDL_Quit();
        return 1;
    }

    if (!dirPath.empty() && isDirWritable(dirPath)) {
        game.savePath = dirPath + "/save.dat";
    } else if (!baseDir.empty() && isDirWritable(baseDir)) {
        game.savePath = baseDir + "/save.dat";
    } else {
        char* pref = SDL_GetPrefPath("EngineGame", game.appName.c_str());
        if (pref) {
            game.savePath = std::string(pref) + "save.dat";
            SDL_free(pref);
        } else {
            game.savePath = "save.dat";
        }
    }

    int winW = 1280, winH = 720;
    computeInitialWindowSize(game.vw, game.vh, winW, winH);

    SDL_Window* window = SDL_CreateWindow(
        game.appName.c_str(),
        winW, winH,
        SDL_WINDOW_RESIZABLE | SDL_WINDOW_HIGH_PIXEL_DENSITY
    );
    if (!window) {
        SDL_Log("SDL_CreateWindow error: %s", SDL_GetError());
        SDL_Quit();
        return 1;
    }
    SDL_SetWindowMinimumSize(window, 320, 180);
    applyWindowIcon(window, game.pak);

    SDL_Renderer* renderer = SDL_CreateRenderer(window, nullptr);
    if (!renderer) {
        SDL_Log("SDL_CreateRenderer error: %s", SDL_GetError());
        SDL_DestroyWindow(window);
        SDL_Quit();
        return 1;
    }
    SDL_SetRenderVSync(renderer, 1);

    game.res.renderer = renderer;
    game.res.initAudio();

    game.onOrientationChange = [&](bool /*land*/) {
        int nw = 0, nh = 0;
        computeInitialWindowSize(game.vw, game.vh, nw, nh);
        SDL_SetWindowSize(window, nw, nh);
    };
    game.onQuit = [&]() {
        game.running = false;
    };

    game.start();

    uint64_t lastTicks = SDL_GetTicksNS();
    bool leftMouseHeld = false;

    while (game.running) {
        int sw = 0, sh = 0;
        SDL_GetCurrentRenderOutputSize(renderer, &sw, &sh);
        if (sw > 0 && sh > 0) game.computeMap(sw, sh);

        SDL_Event ev;
        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_EVENT_QUIT) {
                game.autoSave();
                game.running = false;
                break;
            }
            if (ev.type == SDL_EVENT_WINDOW_RESIZED || ev.type == SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED) {
                SDL_GetCurrentRenderOutputSize(renderer, &sw, &sh);
                if (sw > 0 && sh > 0) game.computeMap(sw, sh);
            } else if (ev.type == SDL_EVENT_WINDOW_MINIMIZED) {
                game.autoSave();
                game.res.pauseAllAudio();
            } else if (ev.type == SDL_EVENT_WINDOW_RESTORED) {
                game.res.resumeAllAudio();
            } else if (game.leftClickTouch && ev.type == SDL_EVENT_MOUSE_BUTTON_DOWN &&
                       ev.button.button == SDL_BUTTON_LEFT) {
                SDL_ConvertEventToRenderCoordinates(renderer, &ev);
                leftMouseHeld = true;
                SDL_CaptureMouse(true);
                game.down(0, game.toVx(ev.button.x), game.toVy(ev.button.y));
            } else if (game.leftClickTouch && ev.type == SDL_EVENT_MOUSE_MOTION &&
                       (leftMouseHeld || (ev.motion.state & SDL_BUTTON_LMASK))) {
                SDL_ConvertEventToRenderCoordinates(renderer, &ev);
                game.move(0, game.toVx(ev.motion.x), game.toVy(ev.motion.y));
            } else if (game.leftClickTouch && ev.type == SDL_EVENT_MOUSE_BUTTON_UP &&
                       ev.button.button == SDL_BUTTON_LEFT) {
                leftMouseHeld = false;
                SDL_CaptureMouse(false);
                game.up(0);
            } else if (ev.type == SDL_EVENT_FINGER_DOWN) {
                float px = ev.tfinger.x * static_cast<float>(sw);
                float py = ev.tfinger.y * static_cast<float>(sh);
                int pid = static_cast<int>(ev.tfinger.fingerID & 0xF);
                game.down(pid, game.toVx(px), game.toVy(py));
            } else if (ev.type == SDL_EVENT_FINGER_MOTION) {
                float px = ev.tfinger.x * static_cast<float>(sw);
                float py = ev.tfinger.y * static_cast<float>(sh);
                int pid = static_cast<int>(ev.tfinger.fingerID & 0xF);
                game.move(pid, game.toVx(px), game.toVy(py));
            } else if (ev.type == SDL_EVENT_FINGER_UP || ev.type == SDL_EVENT_FINGER_CANCELED) {
                int pid = static_cast<int>(ev.tfinger.fingerID & 0xF);
                game.up(pid);
            }
        }

        if (!game.running) break;

        uint64_t now = SDL_GetTicksNS();
        float dt = static_cast<float>(now - lastTicks) / 1e9f;
        lastTicks = now;
        if (dt < 0.f) dt = 0.f;
        if (dt > 0.1f) dt = 0.1f;

        game.update(dt);

        SDL_GetCurrentRenderOutputSize(renderer, &sw, &sh);
        if (sw > 0 && sh > 0) {
            game.draw(renderer, sw, sh);
            SDL_RenderPresent(renderer);
        }

        if (!game.active()) {
            SDL_Delay(8);
        }
    }

    game.autoSave();
    game.res.flushAll();
    game.res.shutdownAudio();
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
