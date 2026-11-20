#pragma once
#include <SDL3/SDL.h>
#include <cstdint>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

class PakReader;

struct RectI {
    int left = 0, top = 0, right = 0, bottom = 0;
    bool empty() const { return right <= left || bottom <= top; }
    int width() const { return right - left; }
    int height() const { return bottom - top; }
};

struct MaskData {
    int w = 0, h = 0;
    std::vector<uint8_t> b;
};

struct TextureEntry {
    SDL_Texture* tex = nullptr;
    int w = 0, h = 0;
    bool hasAlpha = false;
    std::vector<uint8_t> alphaBytes; // w * h alpha channel for smart hit / trim
    bool trimComputed = false;
    RectI trim;
};

struct AudioClip {
    std::vector<float> pcm; // interleaved stereo 44100 Hz float32
    uint64_t frameCount = 0;
};

struct ActiveVoice {
    int nodeId = -1;
    std::string assetName;
    const AudioClip* clip = nullptr;
    uint64_t cursorFrame = 0;
    bool loop = false;
    bool playing = false;
    bool paused = false;
    float volume = 1.0f;
};

struct LabelTexEntry {
    SDL_Texture* tex = nullptr;
    int w = 0, h = 0;
};

class ResourceManager {
public:
    SDL_Renderer* renderer = nullptr;
    const PakReader* pak = nullptr;

    std::unordered_map<std::string, TextureEntry> textures;
    std::unordered_map<std::string, MaskData> masks;
    std::unordered_map<std::string, AudioClip> audioClips;
    std::unordered_map<std::string, std::vector<uint8_t>> fontBytes;
    std::unordered_map<std::string, LabelTexEntry> labelCache;

    // Audio playback state (mixed into SDL_AudioStream)
    SDL_AudioStream* audioStream = nullptr;
    SDL_Mutex* audioMutex = nullptr;
    std::vector<ActiveVoice> voices;
    int soundLoadCount = 0;
    int textureLoadCount = 0;
    int textureReuseCount = 0;

    ~ResourceManager();

    bool initAudio();
    void shutdownAudio();

    const TextureEntry* getTexture(const std::string& name);
    const RectI* getTrimBounds(const std::string& name);
    const MaskData* getMask(const std::string& name, int res, bool maj);

    void prepareScene(const std::unordered_set<std::string>& neededImages,
                      const std::unordered_set<std::string>& neededAudio);
    void preloadSceneImages(const std::unordered_set<std::string>& images);
    void dropUnusedTexture(const std::string& name);
    void flushAll();

    // Audio
    const AudioClip* getAudio(const std::string& name);
    void playSound(int nodeId, const std::string& name, bool loop, float vol);
    void stopSound(int nodeId);
    void pauseAllAudio();
    void resumeAllAudio();
    void releaseAllAudio();
    void pumpAudio();

    // Text / Labels
    const LabelTexEntry* getLabelTexture(const std::string& cacheKey,
                                         const std::string& text,
                                         const std::string& fontAsset,
                                         float fontSize,
                                         bool bold, bool italic,
                                         uint32_t color,
                                         int align,
                                         int maxWidth,
                                         bool wrap,
                                         float outline,
                                         uint32_t ocolor,
                                         bool shadow);

private:
    const std::vector<uint8_t>* loadFontData(const std::string& fontAsset);
    std::vector<uint8_t> defaultFontData;
    bool defaultFontLoaded = false;
    void ensureDefaultFont();
};
