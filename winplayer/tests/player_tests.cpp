#include "../src/game.hpp"
#include "../src/pak.hpp"

#include <SDL3/SDL.h>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

namespace {

// Minimal valid 2x2 RGBA PNG generator (uncompressed deflate store block)
static uint32_t crc32_calc(const uint8_t* data, size_t len) {
    uint32_t crc = 0xFFFFFFFFu;
    for (size_t i = 0; i < len; ++i) {
        crc ^= data[i];
        for (int b = 0; b < 8; ++b) {
            crc = (crc & 1) ? ((crc >> 1) ^ 0xEDB88320u) : (crc >> 1);
        }
    }
    return crc ^ 0xFFFFFFFFu;
}

static uint32_t adler32_calc(const uint8_t* data, size_t len) {
    uint32_t a = 1, b = 0;
    for (size_t i = 0; i < len; ++i) {
        a = (a + data[i]) % 65521;
        b = (b + a) % 65521;
    }
    return (b << 16) | a;
}

static void pushBE32(std::vector<uint8_t>& v, uint32_t x) {
    v.push_back((x >> 24) & 0xFF);
    v.push_back((x >> 16) & 0xFF);
    v.push_back((x >> 8) & 0xFF);
    v.push_back(x & 0xFF);
}

static void pushChunk(std::vector<uint8_t>& png, const char type[4], const std::vector<uint8_t>& data) {
    pushBE32(png, static_cast<uint32_t>(data.size()));
    std::vector<uint8_t> crcBuf(4 + data.size());
    std::memcpy(crcBuf.data(), type, 4);
    if (!data.empty()) std::memcpy(crcBuf.data() + 4, data.data(), data.size());
    png.insert(png.end(), crcBuf.begin(), crcBuf.end());
    pushBE32(png, crc32_calc(crcBuf.data(), crcBuf.size()));
}

static std::vector<uint8_t> makeSolidPng(uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
    std::vector<uint8_t> png = {0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    std::vector<uint8_t> ihdr = {0, 0, 0, 2, 0, 0, 0, 2, 8, 6, 0, 0, 0}; // 2x2 RGBA
    pushChunk(png, "IHDR", ihdr);

    // 2 scanlines: filter(0) + 2*4 RGBA bytes = 18 bytes
    std::vector<uint8_t> raw;
    for (int y = 0; y < 2; ++y) {
        raw.push_back(0);
        for (int x = 0; x < 2; ++x) {
            raw.push_back(r); raw.push_back(g); raw.push_back(b); raw.push_back(a);
        }
    }
    std::vector<uint8_t> zlib;
    zlib.push_back(0x78); zlib.push_back(0x01); // zlib header
    zlib.push_back(0x01); // BFINAL=1, BTYPE=00 (uncompressed)
    uint16_t len = static_cast<uint16_t>(raw.size());
    uint16_t nlen = ~len;
    zlib.push_back(len & 0xFF); zlib.push_back((len >> 8) & 0xFF);
    zlib.push_back(nlen & 0xFF); zlib.push_back((nlen >> 8) & 0xFF);
    zlib.insert(zlib.end(), raw.begin(), raw.end());
    pushBE32(zlib, adler32_calc(raw.data(), raw.size()));
    pushChunk(png, "IDAT", zlib);
    pushChunk(png, "IEND", {});
    return png;
}

static std::vector<uint8_t> makeTinyWav() {
    // 44100 Hz stereo 16-bit PCM, 441 frames (10 ms)
    uint32_t frames = 441;
    uint32_t dataBytes = frames * 4;
    std::vector<uint8_t> wav;
    auto pushStr = [&](const char* s) { wav.insert(wav.end(), s, s + 4); };
    auto pushLE32 = [&](uint32_t x) {
        for (int i = 0; i < 4; ++i) wav.push_back((x >> (8 * i)) & 0xFF);
    };
    auto pushLE16 = [&](uint16_t x) {
        wav.push_back(x & 0xFF); wav.push_back((x >> 8) & 0xFF);
    };
    pushStr("RIFF");
    pushLE32(36 + dataBytes);
    pushStr("WAVE");
    pushStr("fmt ");
    pushLE32(16);
    pushLE16(1); // PCM
    pushLE16(2); // stereo
    pushLE32(44100);
    pushLE32(44100 * 4);
    pushLE16(4);
    pushLE16(16);
    pushStr("data");
    pushLE32(dataBytes);
    wav.resize(44 + dataBytes, 0);
    return wav;
}

struct PakItem {
    std::string path;
    std::vector<uint8_t> data;
};

static void writeTestPak(const std::string& path, const std::vector<PakItem>& items) {
    uint32_t count = static_cast<uint32_t>(items.size());
    uint64_t tocSize = 12;
    for (const auto& it : items) tocSize += 2 + it.path.size() + 16;

    std::ofstream f(path, std::ios::binary | std::ios::trunc);
    assert(f.is_open());
    auto writeU16 = [&](uint16_t v) {
        uint8_t b[2] = {static_cast<uint8_t>(v & 0xFF), static_cast<uint8_t>((v >> 8) & 0xFF)};
        f.write(reinterpret_cast<char*>(b), 2);
    };
    auto writeU32 = [&](uint32_t v) {
        uint8_t b[4] = {
            static_cast<uint8_t>(v & 0xFF), static_cast<uint8_t>((v >> 8) & 0xFF),
            static_cast<uint8_t>((v >> 16) & 0xFF), static_cast<uint8_t>((v >> 24) & 0xFF)
        };
        f.write(reinterpret_cast<char*>(b), 4);
    };
    auto writeU64 = [&](uint64_t v) {
        uint8_t b[8];
        for (int i = 0; i < 8; ++i) b[i] = static_cast<uint8_t>((v >> (8 * i)) & 0xFF);
        f.write(reinterpret_cast<char*>(b), 8);
    };

    f.write("GEPK", 4);
    writeU32(1);
    writeU32(count);

    uint64_t offset = tocSize;
    for (const auto& it : items) {
        writeU16(static_cast<uint16_t>(it.path.size()));
        f.write(it.path.data(), static_cast<std::streamsize>(it.path.size()));
        writeU64(offset);
        writeU64(it.data.size());
        offset += it.data.size();
    }
    for (const auto& it : items) {
        f.write(reinterpret_cast<const char*>(it.data.data()), static_cast<std::streamsize>(it.data.size()));
    }
    f.close();
}

static nlohmann::json makeTestGameJson() {
    using json = nlohmann::json;
    json j = {
        {"name", "MiJuegoPrueba"},
        {"land", true},
        {"st", "keep"},
        {"vm", "viewport"},
        {"vwL", 640}, {"vhL", 360},
        {"vwP", 360}, {"vhP", 640},
        {"bg", 0xFF202020},
        {"asave", true},
        {"lct", true},
        {"start", 1},
        {"next", 100},
        {"nextScene", 3},
        {"enums", {{"Estado", {"Idle", "Activo", "Fin"}}}},
        {"persist", {"score"}},
        {"autoload", json::array({
            {{"t", "start"}, {"f", json::object()}, {"b", json::array({
                {{"t", "var"}, {"f", {{"name", "score"}, {"vtype", "int"}, {"scope", "global"}, {"val", "0"}}}}
            })}}
        })},
        {"scenes", json::array({
            {
                {"id", 1},
                {"name", "Escena 1"},
                {"nodes", json::array({
                    {
                        {"id", 1}, {"name", "FondoS1"}, {"type", "Image"},
                        {"res", "s1_only.png"}, {"x", 0}, {"y", 0}, {"w", 640}, {"h", 360}
                    },
                    {
                        {"id", 2}, {"name", "SpriteAnim"}, {"type", "AnimatedSprite"},
                        {"x", 272}, {"y", 80}, {"w", 96}, {"h", 96},
                        {"fps", 5.0}, {"loop", true}, {"auto", true},
                        {"fr", json::array({"f1.png", "f2.png", "f3.png"})}
                    },
                    {
                        {"id", 3}, {"name", "SpriteCopia"}, {"type", "Image"},
                        {"res", "f1.png"}, {"x", 40}, {"y", 40}, {"w", 48}, {"h", 48}
                    },
                    {
                        {"id", 4}, {"name", "SfxClick"}, {"type", "Sound"},
                        {"snd", "click.wav"}, {"loop", false}, {"auto", false}
                    },
                    {
                        {"id", 5}, {"name", "BotonIr"}, {"type", "Button"},
                        {"text", "Ir a Escena 2"}, {"x", 200}, {"y", 240}, {"w", 240}, {"h", 70}, {"ts", 20},
                        {"script", json::array({
                            {{"t", "press"}, {"f", json::object()}, {"b", json::array({
                                {{"t", "math"}, {"f", {{"name", "score"}, {"op", "+"}, {"val", "10"}}}},
                                {{"t", "play"}, {"f", {{"node", "SfxClick"}, {"dir", "normal"}, {"restart", "true"}, {"loop", "del nodo"}}}},
                                {{"t", "scene"}, {"f", {{"scene", "Escena 2"}}}}
                            })}}
                        })}
                    }
                })}
            },
            {
                {"id", 2},
                {"name", "Escena 2"},
                {"nodes", json::array({
                    {
                        {"id", 10}, {"name", "SpriteAnim2"}, {"type", "AnimatedSprite"},
                        {"x", 40}, {"y", 80}, {"w", 96}, {"h", 96},
                        {"fps", 5.0}, {"loop", true}, {"auto", true},
                        {"fr", json::array({"f1.png", "f2.png", "f3.png"})}
                    },
                    {
                        {"id", 11}, {"name", "CajaMovil"}, {"type", "Image"},
                        {"res", "caja.png"}, {"x", 300}, {"y", 110}, {"w", 100}, {"h", 100}
                    },
                    {
                        {"id", 12}, {"name", "AgarreCaja"}, {"type", "Grab"},
                        {"parent", 11}, {"inh", true},
                        {"x", 0}, {"y", 0}, {"w", 100}, {"h", 100}, {"gm", "arrastrar"}
                    },
                    {
                        {"id", 13}, {"name", "ZonaMeta"}, {"type", "Collision"},
                        {"x", 420}, {"y", 60}, {"w", 80}, {"h", 80},
                        {"script", json::array({
                            {{"t", "collision"}, {"f", {{"node", "CajaMovil"}}}, {"b", json::array({
                                {{"t", "math"}, {"f", {{"name", "score"}, {"op", "+"}, {"val", "50"}}}}
                            })}}
                        })}
                    },
                    {
                        {"id", 14}, {"name", "ControlPC"}, {"type", "Group"},
                        {"script", json::array({
                            {{"t", "func"}, {"f", {{"name", "duplicar"}, {"params", "x"}}}, {"b", json::array({
                                {{"t", "var"}, {"f", {{"name", "res"}, {"vtype", "int"}, {"scope", "local"}, {"val", "x"}}}},
                                {{"t", "math"}, {"f", {{"name", "res"}, {"op", "*"}, {"val", "2"}}}},
                                {{"t", "return"}, {"f", {{"val", "res"}}}}
                            })}},
                            {{"t", "start"}, {"f", json::object()}, {"b", json::array({
                                {{"t", "var"}, {"f", {{"name", "doble"}, {"vtype", "int"}, {"scope", "escena"}, {"val", "0"}}}},
                                {{"t", "call"}, {"f", {{"name", "duplicar"}, {"args", "7"}, {"par", "false"}, {"ret", "doble"}}}},
                                {{"t", "var"}, {"f", {{"name", "fase"}, {"vtype", "Estado"}, {"scope", "escena"}, {"val", "Estado.Idle"}}}},
                                {{"t", "enumnext"}, {"f", {{"enum", "Estado"}, {"name", "fase"}, {"dir", "siguiente"}, {"wrap", "false"}}}},
                                {{"t", "if"}, {"f", {{"expr", "fase = Estado.Activo y doble = 14"}}}, {"b", json::array({
                                    {{"t", "advance"}, {"f", {{"node", "SpriteAnim2"}, {"dir", "derecha"}, {"dist", "20"}, {"secs", "0"}}}}
                                })}}
                            })}},
                            {{"t", "orient"}, {"f", json::object()}, {"b", json::array({
                                {{"t", "var"}, {"f", {{"name", "orientHit"}, {"vtype", "bool"}, {"scope", "escena"}, {"val", "true"}}}}
                            })}}
                        })}
                    },
                    {
                        {"id", 15}, {"name", "BotonSalir"}, {"type", "Button"},
                        {"text", "Salir"}, {"x", 20}, {"y", 270}, {"w", 120}, {"h", 50},
                        {"script", json::array({
                            {{"t", "press"}, {"f", json::object()}, {"b", json::array({
                                {{"t", "orientset"}, {"f", {{"mode", "vertical"}}}},
                                {{"t", "quit"}, {"f", json::object()}}
                            })}}
                        })}
                    }
                })}
            }
        })}
    };
    return j;
}

} // namespace

int main() {
    assert(SDL_Init(SDL_INIT_VIDEO | SDL_INIT_AUDIO));
    SDL_Window* win = SDL_CreateWindow("Test", 640, 360, SDL_WINDOW_RESIZABLE);
    assert(win != nullptr);
    SDL_Renderer* ren = SDL_CreateRenderer(win, "software");
    if (!ren) ren = SDL_CreateRenderer(win, nullptr);
    assert(ren != nullptr);

    // 1. Test default viewport 640x360 & horizontal max 1280x720
    {
        Game gDef;
        assert(gDef.loadFromJsonString(R"({"scenes":[{"id":1,"name":"S1","nodes":[]}]})"));
        assert(gDef.vw == 640 && gDef.vh == 360);
        assert(gDef.leftClickTouch == true);

        Game gCap;
        assert(gCap.loadFromJsonString(R"({"vwL":1920,"vhL":1080,"vwP":720,"vhP":1280,"lct":false,"scenes":[{"id":1,"name":"S1","nodes":[]}]})"));
        assert(gCap.vwL == 1280 && gCap.vhL == 720);
        assert(gCap.leftClickTouch == false);
    }

    // 2. Pack juego.pak for the two-scene test game
    const std::string pakPath = "/tmp/test_juego.pak";
    const std::string savePath = "/tmp/test_save.dat";
    std::remove(savePath.c_str());

    std::vector<PakItem> items = {
        {"game.json", [&]() {
            std::string s = makeTestGameJson().dump();
            return std::vector<uint8_t>(s.begin(), s.end());
        }()},
        {"a/s1_only.png", makeSolidPng(30, 30, 30, 255)},
        {"a/f1.png", makeSolidPng(255, 0, 0, 255)},
        {"a/f2.png", makeSolidPng(0, 255, 0, 255)},
        {"a/f3.png", makeSolidPng(0, 0, 255, 255)},
        {"a/caja.png", makeSolidPng(255, 200, 0, 255)},
        {"a/click.wav", makeTinyWav()}
    };
    writeTestPak(pakPath, items);

    // 3. Load game from juego.pak and verify fast per-scene resource loading & texture reuse
    {
        Game game;
        game.res.renderer = ren;
        game.res.initAudio();
        game.savePath = savePath;
        bool orientFired = false;
        bool quitFired = false;
        game.onOrientationChange = [&](bool land) {
            orientFired = !land;
        };
        game.onQuit = [&]() {
            quitFired = true;
        };

        assert(game.loadFromPakOrDir(pakPath, ""));
        assert(game.vw == 640 && game.vh == 360);
        game.start();
        assert(game.curScene && game.curScene->name == "Escena 1");

        // Per-scene loading: Scene 1 textures loaded, Scene 2 exclusive texture ("caja.png") NOT loaded yet!
        assert(game.res.textures.count("s1_only.png") == 1);
        assert(game.res.textures.count("f1.png") == 1);
        assert(game.res.textures.count("caja.png") == 0);

        // Draw frame to verify texture reuse (SpriteAnim & SpriteCopia share f1.png)
        int reuseBefore = game.res.textureReuseCount;
        game.draw(ren, 1280, 720);
        assert(game.res.textureReuseCount > reuseBefore);

        // AnimatedSprite updates frames over time (fps = 5 -> 0.2s per frame)
        auto anim1 = game.byName("SpriteAnim");
        assert(anim1 && anim1->frame == 0);
        game.update(0.1f);
        game.update(0.1f);
        assert(anim1->frame == 1);
        game.update(0.1f);
        game.update(0.1f);
        assert(anim1->frame == 2);
        game.update(0.1f);
        game.update(0.1f);
        assert(anim1->frame == 0); // looped!

        // Window resized with letterbox bars: 1280x960 (640x360 scales by 2 -> 1280x720 centered at oy=120)
        game.computeMap(1280, 960);
        assert(std::fabs(game.sx - 2.0f) < 0.001f);
        assert(std::fabs(game.ox - 0.0f) < 0.001f);
        assert(std::fabs(game.oy - 120.0f) < 0.001f);

        // Left click on BotonIr (200..440, 240..310) -> screen pixel (640, 120 + 275*2 = 670)
        float vx = game.toVx(640.f);
        float vy = game.toVy(670.f);
        assert(std::fabs(vx - 320.f) < 0.01f);
        assert(std::fabs(vy - 275.f) < 0.01f);
        game.down(0, vx, vy);
        game.up(0);
        game.update(0.016f);

        // Scene transitioned to Escena 2!
        assert(game.curScene && game.curScene->name == "Escena 2");
        assert(game.gvars["score"].i == 10);

        // Per-scene unloading & reuse check:
        // "s1_only.png" unloaded, "caja.png" loaded, "f1.png" kept/reused!
        assert(game.res.textures.count("s1_only.png") == 0);
        assert(game.res.textures.count("caja.png") == 1);
        assert(game.res.textures.count("f1.png") == 1);

        // Check function call + enum + if + advance on Scene 2 startup
        assert(game.svars["doble"].kind == Val::INT && game.svars["doble"].i == 14);
        assert(game.svars["fase"].kind == Val::ENUM && game.svars["fase"].e.name == "Activo");
        auto anim2 = game.byName("SpriteAnim2");
        assert(anim2 && std::fabs(anim2->x - 60.f) < 0.01f); // 40 + 20 = 60

        // Resize window to pillarboxed 1000x360 -> sx=1, ox=(1000-640)/2=180, oy=0
        game.computeMap(1000, 360);
        assert(std::fabs(game.sx - 1.0f) < 0.001f);
        assert(std::fabs(game.ox - 180.0f) < 0.001f);
        assert(std::fabs(game.oy - 0.0f) < 0.001f);

        // Drag CajaMovil (at 300, 110, 100x100) using Grab child via left mouse coordinates in resized window
        auto caja = game.byName("CajaMovil");
        assert(caja && std::fabs(caja->x - 300.f) < 0.01f && std::fabs(caja->y - 110.f) < 0.01f);

        // Screen pixel inside CajaMovil: (180 + 350, 160) -> (530, 160)
        game.down(0, game.toVx(530.f), game.toVy(160.f));
        // Drag to +110 X, -40 Y in screen pixels -> (640, 120) -> logical (460, 120)
        game.move(0, game.toVx(640.f), game.toVy(120.f));
        game.up(0);
        game.update(0.016f);

        assert(std::fabs(caja->x - 410.f) < 0.01f);
        assert(std::fabs(caja->y - 70.f) < 0.01f);
        // Overlapping ZonaMeta (420, 60, 80x80) triggered collision (+50 score -> 60)!
        assert(game.gvars["score"].i == 60);

        // Click BotonSalir (20..140, 270..320) -> screen pixel (180 + 60 = 240, 290)
        game.down(0, game.toVx(240.f), game.toVy(290.f));
        game.up(0);
        game.update(0.016f);

        assert(orientFired == true);
        assert(game.vw == 360 && game.vh == 640);
        assert(quitFired == true);
        assert(game.running == false);

        game.res.flushAll();
        game.res.shutdownAudio();
    }

    // 4. Verify persistent save.dat reloaded score == 60 on fresh launch
    {
        Game game2;
        game2.res.renderer = ren;
        game2.savePath = savePath;
        assert(game2.loadFromPakOrDir(pakPath, ""));
        game2.start();
        assert(game2.gvars["score"].i == 60);
        game2.res.flushAll();
        game2.res.shutdownAudio();
    }

    std::remove(pakPath.c_str());
    std::remove(savePath.c_str());
    SDL_DestroyRenderer(ren);
    SDL_DestroyWindow(win);
    SDL_Quit();

    std::cout << "ALL C++ SDL3 PLAYER TESTS PASSED!\n";
    return 0;
}
