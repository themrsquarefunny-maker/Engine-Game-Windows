#include "resources.hpp"
#include "pak.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>

#define STB_IMAGE_IMPLEMENTATION
#include "../third_party/stb_image.h"

#define STB_TRUETYPE_IMPLEMENTATION
#include "../third_party/stb_truetype.h"

#define STB_VORBIS_HEADER_ONLY
#include "../third_party/stb_vorbis.c"

#define MA_NO_DEVICE_IO
#define MA_NO_THREADING
#define MINIAUDIO_IMPLEMENTATION
#include "../third_party/miniaudio.h"

#undef STB_VORBIS_HEADER_ONLY
#include "../third_party/stb_vorbis.c"

namespace {

static std::vector<uint32_t> utf8ToCodepoints(const std::string& s) {
    std::vector<uint32_t> out;
    size_t i = 0, n = s.size();
    while (i < n) {
        uint8_t c = static_cast<uint8_t>(s[i]);
        uint32_t cp = 0;
        if (c < 0x80) {
            cp = c;
            i += 1;
        } else if ((c & 0xE0) == 0xC0 && i + 1 < n) {
            cp = ((c & 0x1F) << 6) | (static_cast<uint8_t>(s[i + 1]) & 0x3F);
            i += 2;
        } else if ((c & 0xF0) == 0xE0 && i + 2 < n) {
            cp = ((c & 0x0F) << 12) | ((static_cast<uint8_t>(s[i + 1]) & 0x3F) << 6) |
                 (static_cast<uint8_t>(s[i + 2]) & 0x3F);
            i += 3;
        } else if ((c & 0xF8) == 0xF0 && i + 3 < n) {
            cp = ((c & 0x07) << 18) | ((static_cast<uint8_t>(s[i + 1]) & 0x3F) << 12) |
                 ((static_cast<uint8_t>(s[i + 2]) & 0x3F) << 6) |
                 (static_cast<uint8_t>(s[i + 3]) & 0x3F);
            i += 4;
        } else {
            cp = '?';
            i += 1;
        }
        out.push_back(cp);
    }
    return out;
}

static bool readDiskFile(const char* path, std::vector<uint8_t>& out) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f.is_open()) return false;
    auto sz = f.tellg();
    if (sz <= 0) return false;
    f.seekg(0, std::ios::beg);
    out.resize(static_cast<size_t>(sz));
    f.read(reinterpret_cast<char*>(out.data()), sz);
    return static_cast<bool>(f);
}

static void SDLCALL audioStreamCallback(void* userdata, SDL_AudioStream* stream, int additionalAmount, int /*totalAmount*/) {
    if (additionalAmount <= 0 || !userdata) return;
    ResourceManager* rm = static_cast<ResourceManager*>(userdata);
    const int channels = 2;
    int sampleCount = additionalAmount / static_cast<int>(sizeof(float));
    int framesNeeded = sampleCount / channels;
    if (framesNeeded <= 0) return;

    std::vector<float> mix(static_cast<size_t>(framesNeeded * channels), 0.0f);

    SDL_LockMutex(rm->audioMutex);
    for (auto& v : rm->voices) {
        if (!v.playing || v.paused || !v.clip || v.clip->frameCount == 0) continue;
        const float* src = v.clip->pcm.data();
        uint64_t total = v.clip->frameCount;
        float vol = v.volume;
        for (int f = 0; f < framesNeeded; ++f) {
            if (v.cursorFrame >= total) {
                if (v.loop) {
                    v.cursorFrame = 0;
                } else {
                    v.playing = false;
                    break;
                }
            }
            uint64_t idx = v.cursorFrame * 2;
            mix[static_cast<size_t>(f * 2)] += src[idx] * vol;
            mix[static_cast<size_t>(f * 2 + 1)] += src[idx + 1] * vol;
            v.cursorFrame++;
        }
    }
    SDL_UnlockMutex(rm->audioMutex);

    for (float& s : mix) {
        if (s > 1.0f) s = 1.0f;
        else if (s < -1.0f) s = -1.0f;
    }
    SDL_PutAudioStreamData(stream, mix.data(), framesNeeded * channels * static_cast<int>(sizeof(float)));
}

} // namespace

ResourceManager::~ResourceManager() {
    shutdownAudio();
    flushAll();
}

bool ResourceManager::initAudio() {
    if (audioStream) return true;
    audioMutex = SDL_CreateMutex();
    SDL_AudioSpec spec;
    SDL_zero(spec);
    spec.format = SDL_AUDIO_F32;
    spec.channels = 2;
    spec.freq = 44100;
    audioStream = SDL_OpenAudioDeviceStream(SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK, &spec, audioStreamCallback, this);
    if (audioStream) {
        SDL_ResumeAudioStreamDevice(audioStream);
        return true;
    }
    return false;
}

void ResourceManager::shutdownAudio() {
    if (audioStream) {
        SDL_DestroyAudioStream(audioStream);
        audioStream = nullptr;
    }
    if (audioMutex) {
        SDL_DestroyMutex(audioMutex);
        audioMutex = nullptr;
    }
    voices.clear();
}

const TextureEntry* ResourceManager::getTexture(const std::string& name) {
    if (name.empty() || !pak || !renderer) return nullptr;
    auto it = textures.find(name);
    if (it != textures.end()) {
        textureReuseCount++;
        return it->second.tex ? &it->second : nullptr;
    }

    TextureEntry entry;
    std::vector<uint8_t> raw;
    if (!pak->readFile("a/" + name, raw)) {
        textures[name] = entry;
        return nullptr;
    }

    int w = 0, h = 0, ch = 0;
    uint8_t* pixels = stbi_load_from_memory(raw.data(), static_cast<int>(raw.size()), &w, &h, &ch, 4);
    if (!pixels || w <= 0 || h <= 0) {
        if (pixels) stbi_image_free(pixels);
        textures[name] = entry;
        return nullptr;
    }

    entry.w = w;
    entry.h = h;
    entry.hasAlpha = (ch == 4 || ch == 2);
    entry.alphaBytes.resize(static_cast<size_t>(w) * static_cast<size_t>(h));
    bool anyTransparent = false;
    for (int i = 0; i < w * h; ++i) {
        uint8_t a = pixels[i * 4 + 3];
        entry.alphaBytes[static_cast<size_t>(i)] = a;
        if (a != 255) anyTransparent = true;
    }
    entry.hasAlpha = entry.hasAlpha && anyTransparent;

    SDL_Surface* surf = SDL_CreateSurfaceFrom(w, h, SDL_PIXELFORMAT_RGBA32, pixels, w * 4);
    if (surf) {
        entry.tex = SDL_CreateTextureFromSurface(renderer, surf);
        if (entry.tex) {
            SDL_SetTextureBlendMode(entry.tex, SDL_BLENDMODE_BLEND);
            SDL_SetTextureScaleMode(entry.tex, SDL_SCALEMODE_LINEAR);
            textureLoadCount++;
        }
        SDL_DestroySurface(surf);
    }
    stbi_image_free(pixels);

    auto ins = textures.emplace(name, std::move(entry));
    return ins.first->second.tex ? &ins.first->second : nullptr;
}

const RectI* ResourceManager::getTrimBounds(const std::string& name) {
    const TextureEntry* te = getTexture(name);
    if (!te) return nullptr;
    auto& mut = textures[name];
    if (!mut.trimComputed) {
        mut.trimComputed = true;
        if (!mut.hasAlpha) {
            mut.trim = {0, 0, mut.w, mut.h};
        } else {
            int left = mut.w, top = mut.h, right = 0, bottom = 0;
            for (int y = 0; y < mut.h; ++y) {
                for (int x = 0; x < mut.w; ++x) {
                    if (mut.alphaBytes[static_cast<size_t>(y * mut.w + x)] != 0) {
                        left = std::min(left, x);
                        right = std::max(right, x + 1);
                        top = std::min(top, y);
                        bottom = y + 1;
                    }
                }
            }
            if (right == 0) mut.trim = {0, 0, 0, 0};
            else mut.trim = {left, top, right, bottom};
        }
    }
    return &mut.trim;
}

const MaskData* ResourceManager::getMask(const std::string& name, int res, bool maj) {
    if (name.empty()) return nullptr;
    std::string key = name + "|" + std::to_string(res) + "|" + (maj ? "1" : "0");
    auto it = masks.find(key);
    if (it != masks.end()) return &it->second;

    const TextureEntry* te = getTexture(name);
    if (!te) return nullptr;

    MaskData m;
    int bw = te->w, bh = te->h;
    m.w = bw >= bh ? res : std::max(1, static_cast<int>(std::lround(res * static_cast<float>(bw) / bh)));
    m.h = bh >= bw ? res : std::max(1, static_cast<int>(std::lround(res * static_cast<float>(bh) / bw)));
    m.b.resize(static_cast<size_t>(m.w * m.h), 0);

    for (int cy = 0; cy < m.h; ++cy) {
        for (int cx = 0; cx < m.w; ++cx) {
            int x0 = cx * bw / m.w;
            int x1 = std::max(x0 + 1, (cx + 1) * bw / m.w);
            int y0 = cy * bh / m.h;
            int y1 = std::max(y0 + 1, (cy + 1) * bh / m.h);
            int stx = std::max(1, (x1 - x0) / 6);
            int sty = std::max(1, (y1 - y0) / 6);
            int tot = 0, on = 0;
            for (int y = y0; y < y1; y += sty) {
                for (int x = x0; x < x1; x += stx) {
                    tot++;
                    int sx = std::min(x, bw - 1);
                    int sy = std::min(y, bh - 1);
                    if (!te->hasAlpha || te->alphaBytes[static_cast<size_t>(sy * bw + sx)] > 25) on++;
                }
            }
            m.b[static_cast<size_t>(cy * m.w + cx)] = (maj ? (on * 2 >= tot) : (on > 0)) ? 1 : 0;
        }
    }
    auto ins = masks.emplace(key, std::move(m));
    return &ins.first->second;
}

void ResourceManager::prepareScene(const std::unordered_set<std::string>& neededImages,
                                  const std::unordered_set<std::string>& neededAudio) {
    // Release textures NOT used by the incoming scene (while reusing shared ones!)
    for (auto it = textures.begin(); it != textures.end();) {
        if (neededImages.find(it->first) == neededImages.end()) {
            if (it->second.tex) SDL_DestroyTexture(it->second.tex);
            it = textures.erase(it);
        } else {
            ++it;
        }
    }
    for (auto it = masks.begin(); it != masks.end();) {
        size_t sep = it->first.find('|');
        std::string asset = sep == std::string::npos ? it->first : it->first.substr(0, sep);
        if (neededImages.find(asset) == neededImages.end()) {
            it = masks.erase(it);
        } else {
            ++it;
        }
    }
    // Clear label cache across scene transitions
    for (auto& kv : labelCache) {
        if (kv.second.tex) SDL_DestroyTexture(kv.second.tex);
    }
    labelCache.clear();

    // Release audio clips not used in the incoming scene
    releaseAllAudio();
    for (auto it = audioClips.begin(); it != audioClips.end();) {
        if (neededAudio.find(it->first) == neededAudio.end()) {
            it = audioClips.erase(it);
        } else {
            ++it;
        }
    }
}

void ResourceManager::preloadSceneImages(const std::unordered_set<std::string>& images) {
    for (const auto& img : images) {
        getTexture(img);
    }
}

void ResourceManager::dropUnusedTexture(const std::string& name) {
    auto it = textures.find(name);
    if (it != textures.end()) {
        if (it->second.tex) SDL_DestroyTexture(it->second.tex);
        textures.erase(it);
    }
}

void ResourceManager::flushAll() {
    for (auto& kv : textures) {
        if (kv.second.tex) SDL_DestroyTexture(kv.second.tex);
    }
    textures.clear();
    masks.clear();
    for (auto& kv : labelCache) {
        if (kv.second.tex) SDL_DestroyTexture(kv.second.tex);
    }
    labelCache.clear();
    releaseAllAudio();
    audioClips.clear();
    fontBytes.clear();
}

const AudioClip* ResourceManager::getAudio(const std::string& name) {
    if (name.empty() || !pak) return nullptr;
    auto it = audioClips.find(name);
    if (it != audioClips.end()) {
        return it->second.frameCount > 0 ? &it->second : nullptr;
    }

    AudioClip clip;
    std::vector<uint8_t> raw;
    if (!pak->readFile("a/" + name, raw) || raw.empty()) {
        audioClips[name] = clip;
        return nullptr;
    }

    ma_decoder_config cfg = ma_decoder_config_init(ma_format_f32, 2, 44100);
    ma_decoder dec;
    if (ma_decoder_init_memory(raw.data(), raw.size(), &cfg, &dec) == MA_SUCCESS) {
        ma_uint64 frameCount = 0;
        if (ma_decoder_get_length_in_pcm_frames(&dec, &frameCount) == MA_SUCCESS && frameCount > 0) {
            clip.pcm.resize(static_cast<size_t>(frameCount * 2));
            ma_uint64 readFrames = 0;
            ma_decoder_read_pcm_frames(&dec, clip.pcm.data(), frameCount, &readFrames);
            clip.pcm.resize(static_cast<size_t>(readFrames * 2));
            clip.frameCount = readFrames;
        } else {
            // Streaming read for formats where length isn't precomputed (e.g. some mp3)
            std::vector<float> chunk(4096 * 2);
            while (true) {
                ma_uint64 got = 0;
                ma_result res = ma_decoder_read_pcm_frames(&dec, chunk.data(), 4096, &got);
                if (got > 0) {
                    clip.pcm.insert(clip.pcm.end(), chunk.begin(), chunk.begin() + static_cast<size_t>(got * 2));
                    clip.frameCount += got;
                }
                if (res != MA_SUCCESS || got == 0) break;
            }
        }
        ma_decoder_uninit(&dec);
        if (clip.frameCount > 0) soundLoadCount++;
    }

    auto ins = audioClips.emplace(name, std::move(clip));
    return ins.first->second.frameCount > 0 ? &ins.first->second : nullptr;
}

void ResourceManager::playSound(int nodeId, const std::string& name, bool loop, float vol) {
    const AudioClip* clip = getAudio(name);
    if (!clip) return;
    if (!audioMutex) initAudio();
    if (!audioMutex) return;

    SDL_LockMutex(audioMutex);
    ActiveVoice* target = nullptr;
    for (auto& v : voices) {
        if (v.nodeId == nodeId) {
            target = &v;
            break;
        }
    }
    if (!target) {
        voices.push_back(ActiveVoice{});
        target = &voices.back();
    }
    target->nodeId = nodeId;
    target->assetName = name;
    target->clip = clip;
    target->cursorFrame = 0;
    target->loop = loop;
    target->playing = true;
    target->paused = false;
    target->volume = vol;
    SDL_UnlockMutex(audioMutex);
}

void ResourceManager::stopSound(int nodeId) {
    if (!audioMutex) return;
    SDL_LockMutex(audioMutex);
    for (auto& v : voices) {
        if (v.nodeId == nodeId) {
            v.playing = false;
            v.paused = false;
            v.cursorFrame = 0;
        }
    }
    SDL_UnlockMutex(audioMutex);
}

void ResourceManager::pauseAllAudio() {
    if (!audioMutex) return;
    SDL_LockMutex(audioMutex);
    for (auto& v : voices) {
        if (v.playing) v.paused = true;
    }
    SDL_UnlockMutex(audioMutex);
}

void ResourceManager::resumeAllAudio() {
    if (!audioMutex) return;
    SDL_LockMutex(audioMutex);
    for (auto& v : voices) {
        v.paused = false;
    }
    SDL_UnlockMutex(audioMutex);
}

void ResourceManager::releaseAllAudio() {
    if (audioMutex) {
        SDL_LockMutex(audioMutex);
        voices.clear();
        SDL_UnlockMutex(audioMutex);
    } else {
        voices.clear();
    }
}

void ResourceManager::pumpAudio() {
    // Audio stream pulls via SDL callback automatically; nothing needed per frame.
}

void ResourceManager::ensureDefaultFont() {
    if (defaultFontLoaded) return;
    defaultFontLoaded = true;
    const char* candidates[] = {
#ifdef _WIN32
        "C:\\Windows\\Fonts\\segoeui.ttf",
        "C:\\Windows\\Fonts\\arial.ttf",
        "C:\\Windows\\Fonts\\tahoma.ttf",
#else
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
#endif
        nullptr
    };
    for (int i = 0; candidates[i]; ++i) {
        if (readDiskFile(candidates[i], defaultFontData)) return;
    }
}

const std::vector<uint8_t>* ResourceManager::loadFontData(const std::string& fontAsset) {
    if (!fontAsset.empty() && pak) {
        auto it = fontBytes.find(fontAsset);
        if (it != fontBytes.end()) return it->second.empty() ? nullptr : &it->second;
        std::vector<uint8_t> data;
        if (pak->readFile("a/" + fontAsset, data)) {
            auto ins = fontBytes.emplace(fontAsset, std::move(data));
            return &ins.first->second;
        }
        fontBytes[fontAsset] = {};
    }
    ensureDefaultFont();
    return defaultFontData.empty() ? nullptr : &defaultFontData;
}

const LabelTexEntry* ResourceManager::getLabelTexture(const std::string& cacheKey,
                                                      const std::string& text,
                                                      const std::string& fontAsset,
                                                      float fontSize,
                                                      bool bold, bool /*italic*/,
                                                      uint32_t color,
                                                      int align,
                                                      int maxWidth,
                                                      bool wrap,
                                                      float outline,
                                                      uint32_t ocolor,
                                                      bool shadow) {
    auto it = labelCache.find(cacheKey);
    if (it != labelCache.end()) {
        return it->second.tex ? &it->second : nullptr;
    }

    LabelTexEntry entry;
    if (text.empty() || !renderer) {
        labelCache[cacheKey] = entry;
        return nullptr;
    }

    const std::vector<uint8_t>* fontData = loadFontData(fontAsset);
    stbtt_fontinfo font;
    bool hasFont = fontData && stbtt_InitFont(&font, fontData->data(), stbtt_GetFontOffsetForIndex(fontData->data(), 0));
    if (!hasFont) {
        // Fallback minimal bitmap block when running on a minimal container without system TTF fonts
        int cw = 8, ch = 12;
        std::vector<uint32_t> cps = utf8ToCodepoints(text);
        int texW = std::max(1, static_cast<int>(cps.size()) * cw);
        int texH = ch;
        std::vector<uint8_t> rgba(static_cast<size_t>(texW * texH * 4), 0);
        uint8_t r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        for (size_t i = 0; i < cps.size(); ++i) {
            if (cps[i] == ' ' || cps[i] == '\n') continue;
            for (int y = 2; y < ch - 2; ++y) {
                for (int x = 1; x < cw - 1; ++x) {
                    size_t idx = static_cast<size_t>((y * texW + static_cast<int>(i) * cw + x) * 4);
                    rgba[idx + 0] = r; rgba[idx + 1] = g; rgba[idx + 2] = b; rgba[idx + 3] = 255;
                }
            }
        }
        SDL_Surface* surf = SDL_CreateSurfaceFrom(texW, texH, SDL_PIXELFORMAT_RGBA32, rgba.data(), texW * 4);
        if (surf) {
            entry.tex = SDL_CreateTextureFromSurface(renderer, surf);
            if (entry.tex) {
                SDL_SetTextureBlendMode(entry.tex, SDL_BLENDMODE_BLEND);
                entry.w = texW;
                entry.h = texH;
            }
            SDL_DestroySurface(surf);
        }
        auto ins = labelCache.emplace(cacheKey, std::move(entry));
        return ins.first->second.tex ? &ins.first->second : nullptr;
    }

    float scale = stbtt_ScaleForPixelHeight(&font, std::max(6.0f, fontSize));
    int ascent = 0, descent = 0, lineGap = 0;
    stbtt_GetFontVMetrics(&font, &ascent, &descent, &lineGap);
    int lineHeight = std::max(1, static_cast<int>(std::ceil((ascent - descent + lineGap) * scale)));
    int baselineOffset = static_cast<int>(std::ceil(ascent * scale));

    std::vector<uint32_t> cps = utf8ToCodepoints(text);

    // Split into wrapped lines
    struct LineInfo {
        std::vector<uint32_t> glyphs;
        int width = 0;
    };
    std::vector<LineInfo> lines;
    auto measureCp = [&](uint32_t cp) -> int {
        int adv = 0, lsb = 0;
        stbtt_GetCodepointHMetrics(&font, static_cast<int>(cp), &adv, &lsb);
        return static_cast<int>(std::round(adv * scale));
    };

    LineInfo cur;
    int lastSpaceIdx = -1;
    int widthAtLastSpace = 0;
    for (size_t i = 0; i < cps.size(); ++i) {
        uint32_t cp = cps[i];
        if (cp == '\n') {
            lines.push_back(std::move(cur));
            cur = LineInfo{};
            lastSpaceIdx = -1;
            continue;
        }
        int adv = measureCp(cp);
        if (cp == ' ') {
            lastSpaceIdx = static_cast<int>(cur.glyphs.size());
            widthAtLastSpace = cur.width;
        }
        if (wrap && maxWidth > 0 && cur.width + adv > maxWidth && !cur.glyphs.empty()) {
            if (lastSpaceIdx >= 0) {
                LineInfo next;
                for (int k = lastSpaceIdx + 1; k < static_cast<int>(cur.glyphs.size()); ++k) {
                    next.glyphs.push_back(cur.glyphs[static_cast<size_t>(k)]);
                    next.width += measureCp(cur.glyphs[static_cast<size_t>(k)]);
                }
                cur.glyphs.resize(static_cast<size_t>(lastSpaceIdx));
                cur.width = widthAtLastSpace;
                lines.push_back(std::move(cur));
                cur = std::move(next);
                lastSpaceIdx = -1;
                if (cp != ' ') {
                    cur.glyphs.push_back(cp);
                    cur.width += adv;
                }
                continue;
            } else {
                lines.push_back(std::move(cur));
                cur = LineInfo{};
            }
        }
        cur.glyphs.push_back(cp);
        cur.width += adv;
    }
    lines.push_back(std::move(cur));

    int pad = static_cast<int>(std::ceil(outline)) + (shadow ? 4 : 1) + 2;
    int contentW = 1;
    for (const auto& l : lines) contentW = std::max(contentW, l.width);
    int texW = wrap && maxWidth > 0 ? std::max(maxWidth, contentW) + pad * 2 : contentW + pad * 2;
    int texH = std::max(1, static_cast<int>(lines.size()) * lineHeight) + pad * 2;

    std::vector<uint8_t> alphaMap(static_cast<size_t>(texW * texH), 0);
    for (size_t li = 0; li < lines.size(); ++li) {
        const auto& line = lines[li];
        int startX = pad;
        int boxW = texW - pad * 2;
        if (align == 1) startX = pad + (boxW - line.width) / 2;
        else if (align == 2) startX = pad + (boxW - line.width);

        int penX = startX;
        int penY = pad + static_cast<int>(li) * lineHeight + baselineOffset;
        for (size_t gi = 0; gi < line.glyphs.size(); ++gi) {
            uint32_t cp = line.glyphs[gi];
            int ix0, iy0, ix1, iy1;
            stbtt_GetCodepointBitmapBox(&font, static_cast<int>(cp), scale, scale, &ix0, &iy0, &ix1, &iy1);
            int gw = ix1 - ix0, gh = iy1 - iy0;
            if (gw > 0 && gh > 0) {
                std::vector<uint8_t> bmp(static_cast<size_t>(gw * gh));
                stbtt_MakeCodepointBitmap(&font, bmp.data(), gw, gh, gw, scale, scale, static_cast<int>(cp));
                for (int by = 0; by < gh; ++by) {
                    int dy = penY + iy0 + by;
                    if (dy < 0 || dy >= texH) continue;
                    for (int bx = 0; bx < gw; ++bx) {
                        int dx = penX + ix0 + bx;
                        if (dx < 0 || dx >= texW) continue;
                        uint8_t a = bmp[static_cast<size_t>(by * gw + bx)];
                        uint8_t& dst = alphaMap[static_cast<size_t>(dy * texW + dx)];
                        dst = std::max(dst, a);
                        if (bold && dx + 1 < texW) {
                            uint8_t& dst2 = alphaMap[static_cast<size_t>(dy * texW + dx + 1)];
                            dst2 = std::max(dst2, a);
                        }
                    }
                }
            }
            penX += measureCp(cp);
        }
    }

    std::vector<uint8_t> rgba(static_cast<size_t>(texW * texH * 4), 0);
    auto blendPixel = [&](int x, int y, uint8_t sr, uint8_t sg, uint8_t sb, uint8_t sa) {
        if (sa == 0 || x < 0 || y < 0 || x >= texW || y >= texH) return;
        size_t idx = static_cast<size_t>((y * texW + x) * 4);
        float a = sa / 255.0f;
        float da = rgba[idx + 3] / 255.0f;
        float outA = a + da * (1.0f - a);
        if (outA <= 0.0001f) return;
        rgba[idx + 0] = static_cast<uint8_t>((sr * a + rgba[idx + 0] * da * (1.0f - a)) / outA);
        rgba[idx + 1] = static_cast<uint8_t>((sg * a + rgba[idx + 1] * da * (1.0f - a)) / outA);
        rgba[idx + 2] = static_cast<uint8_t>((sb * a + rgba[idx + 2] * da * (1.0f - a)) / outA);
        rgba[idx + 3] = static_cast<uint8_t>(std::min(255.0f, outA * 255.0f));
    };

    if (shadow) {
        int sdx = std::max(1, static_cast<int>(std::round(fontSize / 14.0f)));
        int sdy = sdx;
        for (int y = 0; y < texH; ++y) {
            for (int x = 0; x < texW; ++x) {
                uint8_t a = alphaMap[static_cast<size_t>(y * texW + x)];
                if (a > 0) blendPixel(x + sdx, y + sdy, 0, 0, 0, static_cast<uint8_t>(a * 170 / 255));
            }
        }
    }

    if (outline > 0.0f) {
        int rad = std::max(1, static_cast<int>(std::ceil(outline * 0.5f)));
        uint8_t or_ = (ocolor >> 16) & 0xFF, og = (ocolor >> 8) & 0xFF, ob = ocolor & 0xFF;
        for (int y = 0; y < texH; ++y) {
            for (int x = 0; x < texW; ++x) {
                uint8_t maxA = 0;
                for (int oy = -rad; oy <= rad; ++oy) {
                    int sy = y + oy;
                    if (sy < 0 || sy >= texH) continue;
                    for (int ox = -rad; ox <= rad; ++ox) {
                        int sx = x + ox;
                        if (sx < 0 || sx >= texW) continue;
                        if (ox * ox + oy * oy <= rad * rad) {
                            maxA = std::max(maxA, alphaMap[static_cast<size_t>(sy * texW + sx)]);
                        }
                    }
                }
                if (maxA > 0) blendPixel(x, y, or_, og, ob, maxA);
            }
        }
    }

    uint8_t cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;
    for (int y = 0; y < texH; ++y) {
        for (int x = 0; x < texW; ++x) {
            uint8_t a = alphaMap[static_cast<size_t>(y * texW + x)];
            if (a > 0) blendPixel(x, y, cr, cg, cb, a);
        }
    }

    SDL_Surface* surf = SDL_CreateSurfaceFrom(texW, texH, SDL_PIXELFORMAT_RGBA32, rgba.data(), texW * 4);
    if (surf) {
        entry.tex = SDL_CreateTextureFromSurface(renderer, surf);
        if (entry.tex) {
            SDL_SetTextureBlendMode(entry.tex, SDL_BLENDMODE_BLEND);
            SDL_SetTextureScaleMode(entry.tex, SDL_SCALEMODE_LINEAR);
            entry.w = texW;
            entry.h = texH;
        }
        SDL_DestroySurface(surf);
    }

    auto ins = labelCache.emplace(cacheKey, std::move(entry));
    return ins.first->second.tex ? &ins.first->second : nullptr;
}
