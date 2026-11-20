#pragma once
#include <cstdint>
#include <cstring>
#include <fstream>
#include <string>
#include <unordered_map>
#include <vector>

struct PakEntry {
    uint64_t offset = 0;
    uint64_t size = 0;
};

class PakReader {
public:
    bool isPak = false;
    std::string pakPath;
    std::string looseDir;
    std::unordered_map<std::string, PakEntry> entries;

    bool openPak(const std::string& path) {
        std::ifstream f(path, std::ios::binary);
        if (!f.is_open()) return false;
        char magic[4] = {};
        f.read(magic, 4);
        if (!f || std::memcmp(magic, "GEPK", 4) != 0) return false;

        uint32_t version = 0, count = 0;
        if (!readU32(f, version) || !readU32(f, count)) return false;
        if (version != 1) return false;

        entries.clear();
        entries.reserve(count);
        for (uint32_t i = 0; i < count; ++i) {
            uint16_t nameLen = 0;
            if (!readU16(f, nameLen)) return false;
            std::string name(nameLen, '\0');
            if (nameLen > 0) {
                f.read(&name[0], nameLen);
                if (!f) return false;
            }
            PakEntry e;
            if (!readU64(f, e.offset) || !readU64(f, e.size)) return false;
            entries[name] = e;
        }
        pakPath = path;
        isPak = true;
        return true;
    }

    bool openDir(const std::string& dir) {
        looseDir = dir;
        if (!looseDir.empty() && (looseDir.back() == '/' || looseDir.back() == '\\')) {
            looseDir.pop_back();
        }
        isPak = false;
        std::vector<uint8_t> tmp;
        return readFile("game.json", tmp);
    }

    bool hasFile(const std::string& name) const {
        if (isPak) {
            return entries.find(name) != entries.end();
        }
        std::string p = resolveLoosePath(name);
        std::ifstream f(p, std::ios::binary);
        return f.good();
    }

    bool readFile(const std::string& name, std::vector<uint8_t>& out) const {
        if (isPak) {
            auto it = entries.find(name);
            if (it == entries.end()) return false;
            std::ifstream f(pakPath, std::ios::binary);
            if (!f.is_open()) return false;
            f.seekg(static_cast<std::streamoff>(it->second.offset), std::ios::beg);
            if (!f) return false;
            out.resize(static_cast<size_t>(it->second.size));
            if (it->second.size > 0) {
                f.read(reinterpret_cast<char*>(out.data()), static_cast<std::streamsize>(it->second.size));
                if (!f) return false;
            }
            return true;
        }
        std::string p = resolveLoosePath(name);
        std::ifstream f(p, std::ios::binary | std::ios::ate);
        if (!f.is_open()) return false;
        auto sz = f.tellg();
        if (sz < 0) return false;
        f.seekg(0, std::ios::beg);
        out.resize(static_cast<size_t>(sz));
        if (sz > 0) {
            f.read(reinterpret_cast<char*>(out.data()), sz);
            if (!f) return false;
        }
        return true;
    }

    bool readText(const std::string& name, std::string& out) const {
        std::vector<uint8_t> buf;
        if (!readFile(name, buf)) return false;
        out.assign(reinterpret_cast<const char*>(buf.data()), buf.size());
        return true;
    }

private:
    std::string resolveLoosePath(const std::string& name) const {
        if (name.rfind("a/", 0) == 0) {
            std::string direct = looseDir + "/" + name;
            std::ifstream fd(direct, std::ios::binary);
            if (fd.good()) return direct;
            return looseDir + "/assets/" + name.substr(2);
        }
        return looseDir + "/" + name;
    }

    static bool readU16(std::ifstream& f, uint16_t& v) {
        uint8_t b[2];
        f.read(reinterpret_cast<char*>(b), 2);
        if (!f) return false;
        v = static_cast<uint16_t>(b[0]) | (static_cast<uint16_t>(b[1]) << 8);
        return true;
    }

    static bool readU32(std::ifstream& f, uint32_t& v) {
        uint8_t b[4];
        f.read(reinterpret_cast<char*>(b), 4);
        if (!f) return false;
        v = static_cast<uint32_t>(b[0]) | (static_cast<uint32_t>(b[1]) << 8) |
            (static_cast<uint32_t>(b[2]) << 16) | (static_cast<uint32_t>(b[3]) << 24);
        return true;
    }

    static bool readU64(std::ifstream& f, uint64_t& v) {
        uint8_t b[8];
        f.read(reinterpret_cast<char*>(b), 8);
        if (!f) return false;
        v = 0;
        for (int i = 0; i < 8; ++i) {
            v |= static_cast<uint64_t>(b[i]) << (8 * i);
        }
        return true;
    }
};
