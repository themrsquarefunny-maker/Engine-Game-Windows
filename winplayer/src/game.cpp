#include "game.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <limits>
#include <sstream>

namespace {

static std::string trimStr(const std::string& s) {
    size_t a = 0;
    while (a < s.size() && std::isspace(static_cast<unsigned char>(s[a]))) ++a;
    size_t b = s.size();
    while (b > a && std::isspace(static_cast<unsigned char>(s[b - 1]))) --b;
    return s.substr(a, b - a);
}

static float clamp01(float v) {
    return v < 0.f ? 0.f : (v > 1.f ? 1.f : v);
}

static float pf0(const std::string& s, float d) {
    std::string t = trimStr(s);
    if (t.empty()) return d;
    for (char& ch : t) if (ch == ',') ch = '.';
    char* end = nullptr;
    float v = std::strtof(t.c_str(), &end);
    return (end != t.c_str() && *end == '\0') ? v : d;
}

static bool isIntStr(const std::string& s) {
    if (s.empty()) return false;
    size_t i = (s[0] == '-') ? 1 : 0;
    if (i == s.size()) return false;
    for (; i < s.size(); ++i) {
        if (s[i] < '0' || s[i] > '9') return false;
    }
    return true;
}

static bool isDecStr(const std::string& s) {
    if (s.empty()) return false;
    size_t i = (s[0] == '-') ? 1 : 0;
    bool dot = false, dig = false;
    for (; i < s.size(); ++i) {
        if (s[i] == '.') {
            if (dot) return false;
            dot = true;
        } else if (s[i] >= '0' && s[i] <= '9') {
            dig = true;
        } else {
            return false;
        }
    }
    return dot && dig;
}

static bool isOrTok(const std::string& t) { return t == "o" || t == "or" || t == "||"; }
static bool isAndTok(const std::string& t) { return t == "y" || t == "and" || t == "&&"; }
static bool isCmpTok(const std::string& t) {
    return t == "=" || t == "==" || t == "!=" || t == "<" || t == ">" || t == "<=" || t == ">=";
}

static bool separatorComma(const std::string& s, size_t i) {
    if (s[i] != ',') return false;
    if (i + 1 == s.size() || !std::isdigit(static_cast<unsigned char>(s[i + 1]))) return true;
    if (i > 0 && !std::isdigit(static_cast<unsigned char>(s[i - 1])) &&
        std::string("(+ -\t").find(s[i - 1]) == std::string::npos) {
        return true;
    }
    return false;
}

static std::vector<std::string> splitComma(const std::string& s) {
    std::vector<std::string> out;
    size_t st = 0;
    for (size_t i = 0; i <= s.size(); ++i) {
        if (i == s.size() || s[i] == ',') {
            std::string part = trimStr(s.substr(st, i - st));
            if (!part.empty()) out.push_back(part);
            st = i + 1;
        }
    }
    return out;
}

static std::vector<std::string> splitArgs(const std::string& s) {
    std::vector<std::string> r;
    int dp = 0;
    size_t st = 0;
    bool q = false;
    for (size_t i = 0; i < s.size(); ++i) {
        char c = s[i];
        if (c == '"') q = !q;
        else if (!q && c == '(') dp++;
        else if (!q && c == ')') dp--;
        else if (!q && dp == 0 && c == ',') {
            r.push_back(trimStr(s.substr(st, i - st)));
            st = i + 1;
        }
    }
    r.push_back(trimStr(s.substr(st)));
    return r;
}

static bool collisionEvent(const std::string& type) {
    return type == "collision" || type == "collisionExit" || type == "collisionStay";
}

static std::string fmtNumber(double v) {
    std::ostringstream oss;
    oss << v;
    return oss.str();
}

} // namespace

// ================= Val =================
double Val::asNum() const {
    switch (kind) {
        case INT: return static_cast<double>(i);
        case FLOAT: return f;
        case BOOL: return b ? 1.0 : 0.0;
        case ENUM: return static_cast<double>(e.idx);
        case STRING: {
            std::string t = trimStr(s);
            if (t.empty()) return 0.0;
            char* end = nullptr;
            double v = std::strtod(t.c_str(), &end);
            return (end != t.c_str()) ? v : 0.0;
        }
        default: return 0.0;
    }
}

bool Val::asTruthy() const {
    switch (kind) {
        case NONE: return false;
        case BOOL: return b;
        case INT: return i != 0;
        case FLOAT: return f != 0.0;
        case ENUM: return true;
        case STRING: return !s.empty() && s != "false";
    }
    return false;
}

std::string Val::asString() const {
    switch (kind) {
        case NONE: return "";
        case INT: return std::to_string(i);
        case FLOAT: return fmtNumber(f);
        case BOOL: return b ? "true" : "false";
        case ENUM: return e.name;
        case STRING: return s;
    }
    return "";
}

std::string Val::encode() const {
    switch (kind) {
        case INT: return "i:" + std::to_string(i);
        case FLOAT: return "f:" + fmtNumber(f);
        case BOOL: return std::string("b:") + (b ? "true" : "false");
        case ENUM: return "e:" + e.type + ":" + e.name;
        case STRING: return "s:" + s;
        default: return "s:";
    }
}

void Tw::apply(float k) {
    if (easing == "entrada/salida") k = k * k * (3.f - 2.f * k);
    else if (easing == "salida suave") k = k * (2.f - k);
    if (kind == 0) {
        n->alpha = from[0] + (to[0] - from[0]) * k;
    } else if (kind == 1) {
        if (has[0]) n->x = from[0] + (to[0] - from[0]) * k;
        if (has[1]) n->y = from[1] + (to[1] - from[1]) * k;
    } else if (kind == 3) {
        n->rot = from[0] + (to[0] - from[0]) * k;
    } else {
        if (has[0]) n->w = from[0] + (to[0] - from[0]) * k;
        if (has[1]) n->h = from[1] + (to[1] - from[1]) * k;
    }
}

// ================= Game Construction & JSON =================
Game::Game() {
    autoload = std::make_shared<Node>();
    autoload->id = -2;
    autoload->name = "Autoload";
    autoload->type = "Autoload";
    autoload->visible = false;
    res.pak = &pak;
}

bool Game::loadFromPakOrDir(const std::string& pakPath, const std::string& dirPath) {
    bool ok = false;
    if (!dirPath.empty()) {
        ok = pak.openDir(dirPath);
    } else if (!pakPath.empty()) {
        ok = pak.openPak(pakPath);
    }
    if (!ok) return false;

    std::string jsonText;
    if (!pak.readText("game.json", jsonText)) return false;
    return loadFromJsonString(jsonText);
}

std::shared_ptr<Blk> Game::parseBlk(const nlohmann::json& o) {
    auto b = std::make_shared<Blk>();
    b->t = o.value("t", "");
    if (o.contains("f") && o["f"].is_object()) {
        for (auto it = o["f"].begin(); it != o["f"].end(); ++it) {
            if (it.value().is_string()) b->f[it.key()] = it.value().get<std::string>();
            else b->f[it.key()] = it.value().dump();
        }
    }
    if (o.contains("b") && o["b"].is_array()) b->body = parseBlkList(o["b"]);
    if (o.contains("e") && o["e"].is_array()) b->els = parseBlkList(o["e"]);
    return b;
}

std::vector<std::shared_ptr<Blk>> Game::parseBlkList(const nlohmann::json& a) {
    std::vector<std::shared_ptr<Blk>> l;
    if (!a.is_array()) return l;
    l.reserve(a.size());
    for (const auto& item : a) {
        if (item.is_object()) l.push_back(parseBlk(item));
    }
    return l;
}

std::shared_ptr<Node> Game::parseNode(const nlohmann::json& o) {
    auto n = std::make_shared<Node>();
    n->id = o.value("id", 0);
    n->name = o.value("name", "Nodo");
    n->type = o.value("type", "Image");
    n->parent = o.value("parent", -1);
    n->inherit = o.value("inh", true);
    n->visible = o.value("vis", true);
    n->loop = o.value("loop", false);
    n->autoplay = o.value("auto", false);
    n->fit = o.value("fit", true);
    n->fixed = o.value("fixed", false);
    n->x = o.value("x", 0.f);
    n->y = o.value("y", 0.f);
    n->w = o.value("w", 200.f);
    n->h = o.value("h", 200.f);
    n->alpha = o.value("a", 1.f);
    n->fps = o.value("fps", 8.f);
    n->tsize = o.value("ts", 36.f);
    n->res = o.value("res", "");
    n->snd = o.value("snd", "");
    n->text = o.value("text", "");
    n->zoom = o.value("zoom", 1.f);
    n->smooth = o.value("smooth", 0.f);
    n->follow = o.value("follow", "");
    n->current = o.value("cur", false);
    n->lim = o.value("lim", false);
    n->ll = o.value("ll", 0.f);
    n->lt = o.value("lt", 0.f);
    n->lr = o.value("lr", 1280.f);
    n->lb = o.value("lb", 720.f);
    n->touchAlways = o.value("ta", false);
    n->dim = o.value("dk", true);
    n->smart = o.value("sm", false);
    n->smartRes = o.value("smr", 32);
    n->smartMaj = o.value("smm", false);
    n->bold = o.value("bold", false);
    n->italic = o.value("ital", false);
    n->shadow = o.value("shad", false);
    n->wrap = o.value("wrap", true);
    n->align = o.value("al", 1);
    n->valign = o.value("vl", 1);
    n->color = static_cast<uint32_t>(o.value("col", static_cast<int64_t>(0xFFFFFFFF)));
    n->rot = o.value("rot", 0.f);
    n->anchor = o.value("anc", "");
    n->gtarget = o.value("gt", "");
    n->gmode = o.value("gm", "arrastrar");
    n->limL = o.value("lL", -1.f);
    n->limR = o.value("lR", -1.f);
    n->ocolor = static_cast<uint32_t>(o.value("oc", static_cast<int64_t>(0xFF000000)));
    n->outline = o.value("ow", 0.f);
    n->vol = o.value("vol", 1.f);
    n->font = o.value("font", "");
    n->trimTransparent = o.value("trim", false);
    n->collisionEnabled = o.value("collisionEnabled", true);
    if (o.contains("fr") && o["fr"].is_array()) {
        for (const auto& f : o["fr"]) {
            if (f.is_string()) n->frames.push_back(f.get<std::string>());
        }
    }
    if (o.contains("script") && o["script"].is_array()) {
        n->script = parseBlkList(o["script"]);
    }
    return n;
}

std::vector<std::shared_ptr<Node>> Game::parseNodes(const nlohmann::json& a) {
    std::vector<std::shared_ptr<Node>> l;
    if (!a.is_array()) return l;
    l.reserve(a.size());
    for (const auto& item : a) {
        if (item.is_object()) l.push_back(parseNode(item));
    }
    return l;
}

bool Game::loadFromJsonString(const std::string& jsonStr) {
    nlohmann::json o = nlohmann::json::parse(jsonStr, nullptr, false);
    if (o.is_discarded() || !o.is_object()) return false;

    autoload->script.clear();
    if (o.contains("autoload") && o["autoload"].is_array()) {
        autoload->script = parseBlkList(o["autoload"]);
    }

    scenes.clear();
    landscape = o.value("land", true);
    stretch = o.value("st", "keep");
    viewMode = o.value("vm", "viewport");
    autosave = o.value("asave", true);
    leftClickTouch = o.value("lct", true);
    bg = static_cast<uint32_t>(o.value("bg", static_cast<int64_t>(0xFF202020)));
    appName = o.value("name", appName);

    if (o.contains("vwL")) {
        vwL = std::max(16, o.value("vwL", 640));
        vhL = std::max(16, o.value("vhL", 360));
        vwP = std::max(16, o.value("vwP", 360));
        vhP = std::max(16, o.value("vhP", 640));
    } else {
        int w = std::max(16, o.value("vw", 640));
        int h = std::max(16, o.value("vh", 360));
        if (landscape) {
            vwL = w; vhL = h; vwP = h; vhP = w;
        } else {
            vwP = w; vhP = h; vwL = h; vhL = w;
        }
    }
    // Enforce horizontal max 1280x720
    if (vwL > 1280) vwL = 1280;
    if (vhL > 720) vhL = 720;

    applyOrientation(landscape);

    if (o.contains("scenes") && o["scenes"].is_array()) {
        int idx = 1;
        for (const auto& so : o["scenes"]) {
            Scene sc;
            sc.id = so.value("id", idx);
            sc.name = so.value("name", "Escena " + std::to_string(idx));
            if (so.contains("nodes") && so["nodes"].is_array()) {
                sc.rawNodes = so["nodes"];
                sc.hasRawNodes = true;
            }
            scenes.push_back(std::move(sc));
            idx++;
        }
    } else {
        Scene sc;
        sc.id = 1;
        sc.name = "Escena 1";
        if (o.contains("nodes") && o["nodes"].is_array()) {
            sc.rawNodes = o["nodes"];
            sc.hasRawNodes = true;
        }
        scenes.push_back(std::move(sc));
    }
    if (scenes.empty()) {
        scenes.push_back(Scene{1, "Escena 1", {}, nlohmann::json::array(), false});
    }

    int maxScene = 0;
    for (const auto& sc : scenes) maxScene = std::max(maxScene, sc.id);
    nextSceneId = std::max(o.value("nextScene", 1), maxScene + 1);
    nextId = std::max(o.value("next", 1), 1);
    startId = o.value("start", scenes[0].id);
    if (!sceneById(startId)) startId = scenes[0].id;

    enums.clear();
    if (o.contains("enums") && o["enums"].is_object()) {
        for (auto it = o["enums"].begin(); it != o["enums"].end(); ++it) {
            if (!it.value().is_array()) continue;
            std::vector<std::string> vs;
            for (const auto& v : it.value()) {
                if (v.is_string()) vs.push_back(v.get<std::string>());
            }
            enums[it.key()] = std::move(vs);
        }
    }

    persist.clear();
    if (o.contains("persist") && o["persist"].is_array()) {
        for (const auto& p : o["persist"]) {
            if (p.is_string()) persist.insert(p.get<std::string>());
        }
    }

    return true;
}

void Game::applyOrientation(bool land) {
    landscape = land;
    vw = land ? vwL : vwP;
    vh = land ? vhL : vhP;
}

Scene* Game::sceneById(int id) {
    for (auto& s : scenes) if (s.id == id) return &s;
    return nullptr;
}

Scene* Game::sceneByName(const std::string& n) {
    for (auto& s : scenes) if (s.name == n) return &s;
    return nullptr;
}

std::shared_ptr<Node> Game::byId(int id) const {
    for (const auto& n : nodes) if (n->id == id) return n;
    return nullptr;
}

std::shared_ptr<Node> Game::byName(const std::string& n) const {
    auto it = nameMap.find(n);
    if (it != nameMap.end()) return it->second;
    for (const auto& node : nodes) if (node->name == n) return node;
    return nullptr;
}

// ================= Store (save.dat) =================
void Game::loadStore() {
    storeMap.clear();
    if (savePath.empty()) return;
    std::ifstream f(savePath);
    if (!f.is_open()) return;
    std::string content((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
    nlohmann::json j = nlohmann::json::parse(content, nullptr, false);
    if (j.is_object()) {
        for (auto it = j.begin(); it != j.end(); ++it) {
            if (it.value().is_string()) storeMap[it.key()] = it.value().get<std::string>();
        }
    }
}

void Game::saveStore() {
    if (savePath.empty()) return;
    nlohmann::json j = nlohmann::json::object();
    for (const auto& kv : storeMap) j[kv.first] = kv.second;
    std::ofstream f(savePath, std::ios::trunc);
    if (f.is_open()) f << j.dump();
}

void Game::autoSave() {
    if (!autosave) return;
    for (const auto& nm : persist) {
        auto it = svars.find(nm);
        if (it != svars.end()) {
            storeMap[nm] = it->second.encode();
        } else {
            auto git = gvars.find(nm);
            if (git != gvars.end()) storeMap[nm] = git->second.encode();
        }
    }
    saveStore();
}

void Game::clearStore() {
    storeMap.clear();
    saveStore();
}

// ================= Tree & Geometry =================
void Game::rebuild() {
    order.clear();
    for (auto& n : nodes) {
        n->inOrder = false;
        n->par = (n->parent >= 0) ? byId(n->parent).get() : nullptr;
    }
    addOrder(-1);
}

void Game::addOrder(int pid) {
    for (auto& n : nodes) {
        if (n->inOrder) continue;
        bool root = (pid == -1) ? (n->parent == -1 || n->par == nullptr) : (n->parent == pid);
        if (!root) continue;
        n->inOrder = true;
        order.push_back(n);
        addOrder(n->id);
    }
}

float Game::origin(const Node* n, bool horizontal) {
    float v = 0.f;
    while (n != nullptr && n->inOrder) {
        v += horizontal ? n->x : n->y;
        n = n->inherit ? n->par : nullptr;
    }
    return v;
}

bool Game::inheritsPositionFrom(const Node* node, const Node* ancestor) {
    for (const Node* n = node; n != nullptr && n->inOrder; n = n->inherit ? n->par : nullptr) {
        if (n == ancestor) return true;
    }
    return false;
}

bool Game::constrain(Node* n) {
    Node* box = n->par;
    if (!box || box->type != "Limiter") return false;
    float ox_ = n->inherit ? 0.f : origin(box, true);
    float oy_ = n->inherit ? 0.f : origin(box, false);
    float bw = std::max(0.f, box->w), bh = std::max(0.f, box->h);
    float oldX = n->x, oldY = n->y;
    if (std::fmod(n->rot, 360.f) == 0.f) {
        n->w = std::max(0.f, std::min(n->w, bw));
        n->h = std::max(0.f, std::min(n->h, bh));
        n->x = ox_ + std::max(0.f, std::min(n->x - ox_, bw - n->w));
        n->y = oy_ + std::max(0.f, std::min(n->y - oy_, bh - n->h));
    } else {
        double radians = n->rot * 3.14159265358979323846 / 180.0;
        float c = static_cast<float>(std::cos(radians));
        float s = static_cast<float>(std::sin(radians));
        n->w = std::max(0.f, n->w);
        n->h = std::max(0.f, n->h);
        float ew = std::fabs(c) * n->w + std::fabs(s) * n->h;
        float eh = std::fabs(s) * n->w + std::fabs(c) * n->h;
        if (ew > bw + 0.0001f || eh > bh + 0.0001f) {
            float sc = std::min(ew == 0.f ? 1.f : bw / ew, eh == 0.f ? 1.f : bh / eh);
            n->w *= sc; n->h *= sc;
            ew *= sc; eh *= sc;
        }
        float lx = n->x - ox_ + n->w / 2.f;
        float ly = n->y - oy_ + n->h / 2.f;
        std::shared_ptr<Node> anchor = n->anchor.empty() ? nullptr : byName(n->anchor);
        bool external = anchor != nullptr && !inheritsPositionFrom(anchor.get(), n);
        float cx = lx, cy = ly;
        if (anchor) {
            float ax = origin(anchor.get(), true) - origin(box, true) + anchor->w / 2.f;
            float ay = origin(anchor.get(), false) - origin(box, false) + anchor->h / 2.f;
            cx = ax + c * (lx - ax) - s * (ly - ay);
            cy = ay + s * (lx - ax) + c * (ly - ay);
        }
        float dx = std::max(ew / 2.f, std::min(cx, bw - ew / 2.f)) - cx;
        float dy = std::max(eh / 2.f, std::min(cy, bh - eh / 2.f)) - cy;
        n->x += external ? (c * dx + s * dy) : dx;
        n->y += external ? (-s * dx + c * dy) : dy;
    }
    return std::fabs(n->x - oldX) > 0.0001f || std::fabs(n->y - oldY) > 0.0001f;
}

void Game::compute() {
    for (auto& n : order) {
        Node* pn = n->par;
        bool inh = (pn != nullptr && n->inherit);
        constrain(n.get());
        n->wx = inh ? pn->wx + n->x : n->x;
        n->wy = inh ? pn->wy + n->y : n->y;
        n->wa = inh ? pn->wa * n->alpha : n->alpha;
        n->wvis = (!inh || pn->wvis) && n->visible && !n->norender;
        Mat2D* pm = (inh && pn->rotd) ? &pn->tm : nullptr;
        if (n->rot == 0.f && pm == nullptr) {
            n->rotd = false;
            continue;
        }
        if (pm != nullptr) n->tm = *pm;
        else n->tm.reset();
        if (n->rot != 0.f) {
            std::shared_ptr<Node> an = n->anchor.empty() ? nullptr : byName(n->anchor);
            float px = an ? (origin(an.get(), true) + an->w / 2.f) : (n->wx + n->w / 2.f);
            float py = an ? (origin(an.get(), false) + an->h / 2.f) : (n->wy + n->h / 2.f);
            n->tm.preRotate(n->rot, px, py);
        }
        n->rotd = true;
    }
}

void Game::collisionCorners(const Node* n, float* pts) {
    pts[0] = pts[6] = n->wx;
    pts[1] = pts[3] = n->wy;
    pts[2] = pts[4] = n->wx + n->w;
    pts[5] = pts[7] = n->wy + n->h;
    if (n->rotd) n->tm.mapPoints(pts, 4);
}

bool Game::separates(const float* a, const float* b) {
    for (int i = 0; i < 4; i += 2) {
        float x = a[i + 3] - a[i + 1], y = a[i] - a[i + 2];
        float minA = std::numeric_limits<float>::infinity();
        float maxA = -std::numeric_limits<float>::infinity();
        float minB = std::numeric_limits<float>::infinity();
        float maxB = -std::numeric_limits<float>::infinity();
        for (int j = 0; j < 8; j += 2) {
            float pa = a[j] * x + a[j + 1] * y;
            float pb = b[j] * x + b[j + 1] * y;
            minA = std::min(minA, pa); maxA = std::max(maxA, pa);
            minB = std::min(minB, pb); maxB = std::max(maxB, pb);
        }
        if (maxA <= minB || maxB <= minA) return true;
    }
    return false;
}

bool Game::overlaps(const Node* a, const Node* b) const {
    if (!a || !b || a == b || !a->collisionEnabled || !b->collisionEnabled ||
        !a->wvis || !b->wvis || !a->inOrder || !b->inOrder) return false;
    if (a->w <= 0 || a->h <= 0 || b->w <= 0 || b->h <= 0) return false;
    if (cam && viewMode == "camera" && a->fixed != b->fixed) return false;
    if (!a->rotd && !b->rotd) {
        return a->wx < b->wx + b->w && b->wx < a->wx + a->w &&
               a->wy < b->wy + b->h && b->wy < a->wy + a->h;
    }
    float ca[8], cb[8];
    collisionCorners(a, ca);
    collisionCorners(b, cb);
    return !separates(ca, cb) && !separates(cb, ca);
}

void Game::pollCollisions() {
    compute();
    struct HitPair { std::shared_ptr<Blk> b; std::shared_ptr<Node> n; };
    std::vector<HitPair> hits;
    for (auto& n : nodes) {
        if (n->type != "Collision") continue;
        for (auto& b : n->script) {
            if (!collisionEvent(b->t)) continue;
            auto target = tgt(b.get(), n);
            bool touching = overlaps(n.get(), target.get());
            bool fire = (b->t == "collisionExit")
                        ? (!touching && b->overlapping)
                        : (touching && (b->t == "collisionStay" ? (b->task == nullptr || b->task->done) : !b->overlapping));
            if (fire) hits.push_back({b, n});
            b->overlapping = touching;
        }
    }
    for (auto& h : hits) {
        if (!pendingScene.empty()) break;
        auto task = spawn(h.b->body, h.n);
        if (h.b->t == "collisionStay") h.b->task = task;
    }
}

// ================= Variables & Values =================
Val Game::decodeSaved(const std::string& raw) const {
    if (raw.size() < 2) return Val::makeNone();
    std::string v = raw.substr(2);
    switch (raw[0]) {
        case 'i': return Val::makeInt(std::strtoll(v.c_str(), nullptr, 10));
        case 'f': return Val::makeFloat(std::strtod(v.c_str(), nullptr));
        case 'b': return Val::makeBool(v == "true");
        case 'e': {
            size_t colon = v.find(':');
            if (colon == std::string::npos) return Val::makeNone();
            std::string et = v.substr(0, colon);
            std::string en = v.substr(colon + 1);
            auto it = enums.find(et);
            if (it == enums.end()) return Val::makeNone();
            for (size_t i = 0; i < it->second.size(); ++i) {
                if (it->second[i] == en) return Val::makeEnum(et, en, static_cast<int>(i));
            }
            return Val::makeNone();
        }
        default: return Val::makeString(v);
    }
}

Val Game::castEnum(const std::string& type, const Val& v) const {
    auto it = enums.find(type);
    if (it == enums.end() || it->second.empty()) return Val::makeString(v.asString());
    std::string s = v.asString();
    size_t d = s.rfind('.');
    if (d != std::string::npos) s = s.substr(d + 1);
    for (size_t i = 0; i < it->second.size(); ++i) {
        if (it->second[i] == s) return Val::makeEnum(type, s, static_cast<int>(i));
    }
    return Val::makeEnum(type, it->second[0], 0);
}

Val Game::castType(const std::string& type, const Val& v) const {
    if (type == "int") return Val::makeInt(static_cast<int64_t>(v.asNum()));
    if (type == "float") return Val::makeFloat(v.asNum());
    if (type == "bool") return Val::makeBool(v.asTruthy());
    if (type == "string") return Val::makeString(v.asString());
    return castEnum(type, v);
}

Val Game::castLike(const Val& cur, const Val& v) const {
    switch (cur.kind) {
        case Val::INT: return Val::makeInt(static_cast<int64_t>(v.asNum()));
        case Val::FLOAT: return Val::makeFloat(v.asNum());
        case Val::BOOL: return Val::makeBool(v.asTruthy());
        case Val::ENUM: return castEnum(cur.e.type, v);
        default: return Val::makeString(v.asString());
    }
}

Frame* Game::fnFrame() {
    if (!curTask || curTask->fns == 0) return nullptr;
    for (int i = static_cast<int>(curTask->stack.size()) - 1; i >= 0; --i) {
        if (curTask->stack[static_cast<size_t>(i)].fn) return &curTask->stack[static_cast<size_t>(i)];
    }
    return nullptr;
}

Val Game::getVar(const std::string& name, Node* self) {
    Frame* fr = fnFrame();
    if (fr) {
        auto it = fr->loc.find(name);
        if (it != fr->loc.end()) return it->second;
    }
    if (self) {
        auto it = self->vars.find(name);
        if (it != self->vars.end()) return it->second;
    }
    auto sit = svars.find(name);
    if (sit != svars.end()) return sit->second;
    auto git = gvars.find(name);
    if (git != gvars.end()) return git->second;
    return Val::makeNone();
}

void Game::putVar(const std::string& name, const Val& v, Node* self) {
    Frame* fr = fnFrame();
    if (fr && fr->loc.find(name) != fr->loc.end()) {
        fr->loc[name] = v;
        return;
    }
    if (self && self->vars.find(name) != self->vars.end()) {
        self->vars[name] = v;
    } else if (svars.find(name) != svars.end()) {
        svars[name] = v;
    } else {
        gvars[name] = v;
    }
}

Val Game::prop(const Node* n, const std::string& pr) const {
    if (!n) return Val::makeNone();
    if (pr == "x") return Val::makeFloat(n->x);
    if (pr == "y") return Val::makeFloat(n->y);
    if (pr == "w") return Val::makeFloat(n->w);
    if (pr == "h") return Val::makeFloat(n->h);
    if (pr == "alpha" || pr == "modulate") return Val::makeFloat(n->alpha);
    if (pr == "visible") return Val::makeBool(n->visible);
    if (pr == "text") return Val::makeString(n->text);
    if (pr == "fps") return Val::makeFloat(n->fps);
    if (pr == "frame") return Val::makeInt(n->frame);
    if (pr == "zoom") return Val::makeFloat(n->zoom);
    if (pr == "rot") return Val::makeFloat(n->rot);
    return Val::makeNone();
}

Val Game::val(const std::string& rawS, Node* self) {
    std::string s = trimStr(rawS);
    if (s.empty()) return Val::makeString("");
    if (s == "true") return Val::makeBool(true);
    if (s == "false") return Val::makeBool(false);
    if (s.size() >= 2 && s.front() == '"' && s.back() == '"') return Val::makeString(s.substr(1, s.size() - 2));
    if (isIntStr(s)) return Val::makeInt(std::strtoll(s.c_str(), nullptr, 10));
    if (isDecStr(s)) return Val::makeFloat(std::strtod(s.c_str(), nullptr));

    Val gv = getVar(s, self);
    if (!gv.isNone()) return gv;

    size_t d = s.find('.');
    if (d != std::string::npos && d > 0) {
        std::string nn = s.substr(0, d);
        std::string rest = s.substr(d + 1);
        Val ov = getVar(nn, self);
        if (ov.kind == Val::ENUM) {
            if (rest == "indice" || rest == "índice") return Val::makeInt(ov.e.idx);
            if (rest == "nombre") return Val::makeString(ov.e.name);
        }
        auto eit = enums.find(nn);
        if (eit != enums.end()) {
            if (rest == "cantidad") return Val::makeInt(static_cast<int64_t>(eit->second.size()));
            for (size_t i = 0; i < eit->second.size(); ++i) {
                if (eit->second[i] == rest) return Val::makeEnum(nn, rest, static_cast<int>(i));
            }
        }
        Node* n = (nn == "self") ? self : byName(nn).get();
        if (n) {
            Val v = prop(n, rest);
            if (v.isNone()) {
                auto vit = n->vars.find(rest);
                if (vit != n->vars.end()) v = vit->second;
            }
            if (!v.isNone()) return v;
        }
    }
    return Val::makeString(s);
}

bool Game::cmp(const Val& lIn, const std::string& op, const Val& rIn) {
    Val l = lIn, r = rIn;
    if (l.kind == Val::ENUM && r.kind == Val::ENUM && l.e.type == r.e.type) {
        l = Val::makeInt(l.e.idx);
        r = Val::makeInt(r.e.idx);
    }
    if (l.isNumber() && r.isNumber()) {
        double x = l.asNum(), y = r.asNum();
        if (op == "<") return x < y;
        if (op == ">") return x > y;
        if (op == "<=") return x <= y;
        if (op == ">=") return x >= y;
        if (op == "!=") return x != y;
        return x == y;
    }
    bool eq = (l.asString() == r.asString());
    if (op == "!=") return !eq;
    return (op == "=" || op == "==" || op.empty()) && eq;
}

// ================= Expression Parser =================
std::vector<std::string> Game::tokenize(const std::string& s) {
    std::vector<std::string> r;
    size_t i = 0, n = s.size();
    while (i < n) {
        char c = s[i];
        if (std::isspace(static_cast<unsigned char>(c))) {
            i++;
        } else if (c == '"') {
            size_t j = i + 1;
            while (j < n && s[j] != '"') {
                if (s[j++] == '\\' && j < n) j++;
            }
            r.push_back(s.substr(i, std::min(j + 1, n) - i));
            i = j + 1;
        } else if (separatorComma(s, i)) {
            r.push_back(",");
            i++;
        } else if (std::string("=!<>&|()").find(c) != std::string::npos) {
            size_t j = i + 1;
            if (j < n) {
                std::string two;
                two.push_back(c);
                two.push_back(s[j]);
                if (two == "==" || two == "!=" || two == "<=" || two == ">=" || two == "&&" || two == "||") {
                    j++;
                }
            }
            r.push_back(s.substr(i, j - i));
            i = j;
        } else {
            size_t j = i;
            while (j < n && !std::isspace(static_cast<unsigned char>(s[j])) &&
                   std::string("=!<>&|()\"").find(s[j]) == std::string::npos && !separatorComma(s, j)) {
                j++;
            }
            r.push_back(s.substr(i, j - i));
            i = j;
        }
    }
    return r;
}

const std::vector<std::string>& Game::toks(Blk* b) {
    std::string ex = b->s("expr");
    if (!b->ctkValid || ex != b->ck) {
        b->ck = ex;
        b->ctk = tokenize(ex);
        b->ctkValid = true;
        b->hasCta = false;
        b->cta.clear();
        for (size_t i = 0; i + 2 < b->ctk.size(); ++i) {
            if (b->ctk[i] == "por" && b->ctk[i + 1] == "(") {
                b->cta = b->ctk[i + 2];
                b->hasCta = true;
                break;
            }
        }
    }
    return b->ctk;
}

float Game::argF(const std::string& a) {
    Val o = val(a, cself);
    return o.isNumber() ? static_cast<float>(o.asNum()) : pf0(o.asString(), 0.f);
}

float Game::tsecOf(Blk* b, Node* self) {
    cself = self;
    if (b->hasField("expr")) {
        toks(b);
        if (b->hasCta) return argF(b->cta);
    }
    return pf0(b->s("tsec"), 0.f);
}

const std::string* Game::pk() const {
    return ci < ct.size() ? &ct[ci] : nullptr;
}

Node* Game::collisionNode(const std::string& token) {
    if (!token.empty() && token.front() == '"') {
        if (token.find('\\') == std::string::npos && token.size() >= 2) {
            return byName(token.substr(1, token.size() - 2)).get();
        }
        nlohmann::json parsed = nlohmann::json::parse(token, nullptr, false);
        if (parsed.is_string()) return byName(parsed.get<std::string>()).get();
        return nullptr;
    }
    return (token == "self") ? cself : byName(token).get();
}

bool Game::evalExpr(Blk* b, Node* self) {
    ct = toks(b);
    ci = 0;
    cself = self;
    collisionGeometry = false;
    bool ok = true;
    try {
        bool r = pOr();
        ok = (ci >= ct.size() && r);
    } catch (...) {
        ok = false;
    }
    return ok;
}

bool Game::pOr() {
    bool r = pAnd();
    while (pk() && isOrTok(*pk())) {
        ci++;
        bool q = pAnd();
        r = r || q;
    }
    return r;
}

bool Game::pAnd() {
    bool r = pNot();
    while (pk() && isAndTok(*pk())) {
        ci++;
        bool q = pNot();
        r = r && q;
    }
    return r;
}

bool Game::pNot() {
    const std::string* t = pk();
    if (t && (*t == "!" || *t == "not" || *t == "no")) {
        const std::string* nx = (ci + 1 < ct.size()) ? &ct[ci + 1] : nullptr;
        if (*t == "!" || (nx && !isCmpTok(*nx) && !isAndTok(*nx) && !isOrTok(*nx) && *nx != ")")) {
            ci++;
            return !pNot();
        }
    }
    return pCmp();
}

bool Game::pCmp() {
    const std::string* tp = pk();
    if (!tp) throw 1;
    std::string t = *tp;
    ci++;
    if (t == "(") {
        bool r = pOr();
        if (!pk() || *pk() != ")") throw 1;
        ci++;
        return r;
    }
    if (pk() && *pk() == "(") {
        ci++;
        if (!pk() || *pk() == ")") throw 1;
        std::string a = *pk();
        ci++;
        if (t == "colisiona") {
            if (!pk() || *pk() != ",") throw 1;
            ci++;
            if (!pk() || *pk() == ")") throw 1;
            std::string bName = *pk();
            ci++;
            if (!pk() || *pk() != ")") throw 1;
            ci++;
            Node* first = collisionNode(a);
            Node* second = collisionNode(bName);
            if (!first || !second || (first->type != "Collision" && second->type != "Collision")) return false;
            if (!collisionGeometry) {
                compute();
                collisionGeometry = true;
            }
            return overlaps(first, second);
        }
        if (!pk() || *pk() != ")") throw 1;
        ci++;
        if (t == "por") return true;
        if (t == "prob") {
            std::uniform_real_distribution<float> dist(0.f, 1.f);
            return !probLive || dist(rnd) < argF(a);
        }
        throw 1;
    }
    if ((!pk() || !isCmpTok(*pk())) && getVar(t, cself).isNone()) {
        if (t == "pulsado") return cself != nullptr && cself->pressed;
        if (t == "dedo_dentro") return fingerIn(cself);
        if (t == "dedo_fuera") return !fingerIn(cself);
    }
    Val l = val(t, cself);
    if (pk() && isCmpTok(*pk())) {
        std::string op = ct[ci++];
        if (!pk()) throw 1;
        std::string rt = *pk();
        ci++;
        return cmp(l, op, val(rt, cself));
    }
    return l.asTruthy();
}

bool Game::condCore(Blk* b, Node* self) {
    bool resOk = true;
    if (b->hasField("expr")) {
        std::string ex = trimStr(b->s("expr"));
        if (ex.empty()) {
            resOk = pf0(b->s("nc"), 0) > 0 || !trimStr(b->s("prob")).empty() || !b->s("touch").empty() || pf0(b->s("tsec"), 0) > 0;
        } else {
            resOk = evalExpr(b, self);
        }
    } else {
        auto cond1 = [&](const std::string& ls, const std::string& op, const std::string& rs, bool notFlag) {
            Val l = val(ls, self);
            bool r = trimStr(rs).empty() ? l.asTruthy() : cmp(l, op, val(rs, self));
            return notFlag ? !r : r;
        };
        bool extra = pf0(b->s("nc"), 0) > 0 || !trimStr(b->s("prob")).empty() || !b->s("touch").empty() || pf0(b->s("tsec"), 0) > 0;
        if (!(trimStr(b->s("l")).empty() && trimStr(b->s("r")).empty() && extra)) {
            resOk = cond1(b->s("l"), b->s("op"), b->s("r"), b->b("not"));
        }
        int nc = static_cast<int>(pf0(b->s("nc"), 0));
        for (int i = 1; i <= nc && resOk; ++i) {
            std::string k = "c" + std::to_string(i) + "_";
            std::string l = b->s(k + "l"), r = b->s(k + "r");
            if (trimStr(l).empty() && trimStr(r).empty()) continue;
            resOk = cond1(l, b->s(k + "op"), r, b->b(k + "n"));
        }
    }
    std::string tc = b->s("touch");
    if (resOk && tc == "hold") resOk = self != nullptr && self->pressed;
    else if (resOk && tc == "swipe") resOk = self != nullptr && self->swiped;
    else if (resOk && tc == "in") resOk = fingerIn(self);
    else if (resOk && tc == "out") resOk = !fingerIn(self);
    return resOk;
}

bool Game::probPass(Blk* b) {
    std::string ps = trimStr(b->s("prob"));
    if (ps.empty()) return true;
    std::uniform_real_distribution<float> dist(0.f, 1.f);
    return dist(rnd) < pf0(ps, 1.f);
}

bool Game::cond(Blk* b, Node* self) {
    return condCore(b, self) && probPass(b);
}

int Game::condState(Task* t, Blk* b) {
    float ts = tsecOf(b, t->self.get());
    if (ts <= 0.f) return cond(b, t->self.get()) ? 1 : 0;
    probLive = false;
    bool ok = condCore(b, t->self.get());
    probLive = true;
    std::string tmMode = b->s("tm");
    if (tmMode.rfind("reloj", 0) == 0) {
        if (!ok) {
            tclk.erase(b);
            return 0;
        }
        auto it = tclk.find(b);
        if (it == tclk.end()) {
            tclk[b] = static_cast<float>(clk);
            return 0;
        }
        if (clk - it->second < ts) return 0;
        tclk.erase(b);
        return probPass(b) ? 1 : 0;
    }
    if (!ok) {
        t->tm.erase(b);
        return 0;
    }
    auto it = t->tm.find(b);
    if (it == t->tm.end()) {
        t->tm[b] = {0.f, 0.f};
    } else {
        it->second.first += curDt;
    }
    if (t->tm[b].first >= ts) {
        t->tm.erase(b);
        return probPass(b) ? 1 : 0;
    }
    return 2;
}

// ================= Input & Hit Testing =================
bool Game::wantsTouch(const std::vector<std::shared_ptr<Blk>>& l) {
    for (const auto& b : l) {
        if (b->t == "press" || b->t == "hold") return true;
        if ((b->t == "if" || b->t == "elif" || b->t == "while") && !b->s("touch").empty()) return true;
        if (wantsTouch(b->body) || wantsTouch(b->els)) return true;
    }
    return false;
}

bool Game::touchable(Node* n) {
    if (n->type == "Button" || n->type == "ImageButton" || n->type == "Grab") return true;
    if (n->tch < 0) n->tch = wantsTouch(n->script) ? 1 : 0;
    return n->tch == 1;
}

bool Game::scanSwipe(const std::vector<std::shared_ptr<Blk>>& l) {
    for (const auto& b : l) {
        if (b->s("touch") == "swipe") return true;
        if (scanSwipe(b->body) || scanSwipe(b->els)) return true;
    }
    return false;
}

bool Game::inNode(const Node* n, float px, float py) {
    if (n->rotd) {
        Mat2D hm;
        if (!n->tm.invert(hm)) return false;
        hm.mapPoint(px, py);
    }
    mx = px;
    my = py;
    return px >= n->wx && px <= n->wx + n->w && py >= n->wy && py <= n->wy + n->h;
}

std::string Game::maskImage(const Node* n) const {
    if (n->type == "AnimatedSprite") {
        if (n->frames.empty()) return "";
        int idx = std::min(std::max(0, n->frame), static_cast<int>(n->frames.size()) - 1);
        return n->frames[static_cast<size_t>(idx)];
    }
    return n->res;
}

bool Game::maskHit(const Node* n, float px, float py) {
    std::string name = maskImage(n);
    if (name.empty()) return true;
    const MaskData* m = res.getMask(name, n->smartRes, n->smartMaj);
    if (!m || m->w <= 0 || m->h <= 0) return true;
    int cx = static_cast<int>((px - n->wx) / std::max(0.001f, n->w) * m->w);
    int cy = static_cast<int>((py - n->wy) / std::max(0.001f, n->h) * m->h);
    cx = std::max(0, std::min(m->w - 1, cx));
    cy = std::max(0, std::min(m->h - 1, cy));
    return m->b[static_cast<size_t>(cy * m->w + cx)] != 0;
}

bool Game::fingerIn(const Node* n) {
    if (!n || !n->wvis || (n->wa <= 0.003f && !n->touchAlways)) return false;
    for (int i = 0; i < 16; ++i) {
        if (!pton[i]) continue;
        float vx = ptx[i], vy = pty[i];
        bool c = useCam && !n->fixed;
        float px = c ? (vx - vw / 2.f) / camZ + camX : vx;
        float py = c ? (vy - vh / 2.f) / camZ + camY : vy;
        if (inNode(n, px, py) && (!n->smart || maskHit(n, mx, my))) return true;
    }
    return false;
}

std::shared_ptr<Node> Game::hit(float vx, float vy, bool onlyTouchable) {
    float cx = useCam ? (vx - vw / 2.f) / camZ + camX : vx;
    float cy = useCam ? (vy - vh / 2.f) / camZ + camY : vy;
    for (int i = static_cast<int>(order.size()) - 1; i >= 0; --i) {
        auto& n = order[static_cast<size_t>(i)];
        if (!n->wvis) continue;
        if (onlyTouchable && ((n->wa <= 0.003f && !n->touchAlways) || !touchable(n.get()))) continue;
        float px = (useCam && !n->fixed) ? cx : vx;
        float py = (useCam && !n->fixed) ? cy : vy;
        if (inNode(n.get(), px, py)) {
            if (onlyTouchable && n->smart && !maskHit(n.get(), mx, my)) continue;
            return n;
        }
    }
    return nullptr;
}

Node* Game::grabTarget(Node* g) const {
    Node* t = g->gtarget.empty() ? g->par : byName(g->gtarget).get();
    return t ? t : g;
}

float Game::grabAngle(Node* g, Node* t) const {
    Node* an = g->anchor.empty() ? nullptr : byName(g->anchor).get();
    float px = an ? an->wx + an->w / 2.f : t->wx + t->w / 2.f;
    float py = an ? an->wy + an->h / 2.f : t->wy + t->h / 2.f;
    return static_cast<float>(std::atan2(g->gy - py, g->gx - px) * 180.0 / 3.14159265358979323846);
}

void Game::grabStart(const std::shared_ptr<Node>& g, float vx, float vy) {
    Node* t = grabTarget(g.get());
    g->gx = (useCam && !g->fixed) ? (vx - vw / 2.f) / camZ + camX : vx;
    g->gy = (useCam && !g->fixed) ? (vy - vh / 2.f) / camZ + camY : vy;
    g->gacc = 0.f;
    g->grot = t->rot;
    g->ga = grabAngle(g.get(), t);
    if (std::find(grabs.begin(), grabs.end(), g) == grabs.end()) grabs.push_back(g);
}

void Game::grabMove(const std::shared_ptr<Node>& g, float vx, float vy) {
    Node* t = grabTarget(g.get());
    float cx = (useCam && !g->fixed) ? (vx - vw / 2.f) / camZ + camX : vx;
    float cy = (useCam && !g->fixed) ? (vy - vh / 2.f) / camZ + camY : vy;
    float dx = cx - g->gx, dy = cy - g->gy;
    g->gx = cx;
    g->gy = cy;
    if (g->gmode == "rotar") {
        float a = grabAngle(g.get(), t), d = a - g->ga;
        if (d > 180.f) d -= 360.f;
        else if (d < -180.f) d += 360.f;
        g->ga = a;
        g->gacc += d;
        float r = g->grot + g->gacc;
        float lo = g->limL >= 0.f ? t->rot0 - g->limL : -1e9f;
        float hi = g->limR >= 0.f ? t->rot0 + g->limR : 1e9f;
        if (r < lo) {
            r = lo;
            g->gacc = lo - g->grot;
        } else if (r > hi) {
            r = hi;
            g->gacc = hi - g->grot;
        }
        t->rot = r;
    } else {
        t->x += dx;
        t->y += dy;
    }
    constrain(t);
    compute();
}

void Game::down(int pid, float vx, float vy) {
    if (pid >= 0 && pid < 16) {
        ptx[pid] = vx;
        pty[pid] = vy;
        pton[pid] = true;
    }
    auto n = hit(vx, vy, true);
    if (!n) return;
    n->pressed = true;
    n->pid = pid;
    if (n->type == "Grab") grabStart(n, vx, vy);
    for (auto& b : n->script) {
        if (b->t == "press") {
            spawn(b->body, n);
        } else if (b->t == "hold") {
            Hold h;
            h.n = n;
            h.b = b.get();
            h.pid = pid;
            holds.push_back(std::move(h));
        }
    }
}

void Game::move(int pid, float vx, float vy) {
    if (pid >= 0 && pid < 16) {
        ptx[pid] = vx;
        pty[pid] = vy;
        pton[pid] = true;
    }
    for (int i = static_cast<int>(grabs.size()) - 1; i >= 0; --i) {
        if (grabs[static_cast<size_t>(i)]->pid == pid) {
            grabMove(grabs[static_cast<size_t>(i)], vx, vy);
        }
    }
    if (swipeState < 0) {
        swipeState = 0;
        for (const auto& n : nodes) {
            if (scanSwipe(n->script)) {
                swipeState = 1;
                break;
            }
        }
    }
    if (swipeState == 0) return;
    auto h = hit(vx, vy, true);
    for (auto& n : nodes) {
        if (n->swPid == pid && n != h) {
            n->swPid = -1;
            n->swiped = false;
        }
    }
    if (h && h->swPid != pid) {
        h->swPid = pid;
        h->swiped = true;
    }
}

void Game::up(int pid) {
    if (pid >= 0 && pid < 16) pton[pid] = false;
    for (auto& n : nodes) {
        if (n->pid == pid) {
            n->pid = -1;
            n->pressed = false;
            grabs.erase(std::remove(grabs.begin(), grabs.end(), n), grabs.end());
        }
        if (n->swPid == pid) {
            n->swPid = -1;
            n->swiped = false;
        }
    }
}

void Game::upAll() {
    for (int i = 0; i < 16; ++i) pton[i] = false;
    grabs.clear();
    for (auto& n : nodes) {
        n->pid = -1;
        n->pressed = false;
        n->swPid = -1;
        n->swiped = false;
    }
}

// ================= Actions & Script Execution =================
std::shared_ptr<Node> Game::tgt(Blk* b, const std::shared_ptr<Node>& self) {
    std::string n = trimStr(b->s("node"));
    if (n.empty() || n == "self") return self;
    return byName(n);
}

void Game::setOrient(bool land) {
    if (land == landscape) return;
    applyOrientation(land);
    if (onOrientationChange) onOrientationChange(land);
    orientChanged = true;
}

void Game::fitNode(Node* n) {
    std::string a = (n->type == "AnimatedSprite")
                    ? (n->frames.empty() ? "" : n->frames[0])
                    : n->res;
    const TextureEntry* te = res.getTexture(a);
    if (te) {
        n->w = static_cast<float>(te->w);
        n->h = static_cast<float>(te->h);
    }
}

void Game::dropRes(Node* n) {
    std::vector<std::string> rs = n->frames;
    rs.push_back(n->res);
    for (const auto& r : rs) {
        if (r.empty()) continue;
        bool used = false;
        for (const auto& q : nodes) {
            if (q.get() != n && !q->norender &&
                (q->res == r || std::find(q->frames.begin(), q->frames.end(), r) != q->frames.end())) {
                used = true;
                break;
            }
        }
        if (!used) res.dropUnusedTexture(r);
    }
}

void Game::applyTween(Tw& tween, float progress) {
    tween.apply(progress);
    if (tween.kind != 0) constrain(tween.n.get());
}

void Game::layer(const std::shared_ptr<Node>& n, const std::string& mode) {
    bool upDir = (mode == "subir" || mode == "al frente");
    auto it = std::find(nodes.begin(), nodes.end(), n);
    if (it == nodes.end()) return;
    int i = static_cast<int>(it - nodes.begin());
    int pos = i, st = upDir ? 1 : -1;
    for (int j = i + st; j >= 0 && j < static_cast<int>(nodes.size()); j += st) {
        if (nodes[static_cast<size_t>(j)]->parent != n->parent) continue;
        pos = j;
        if (mode.rfind("al ", 0) != 0) break;
    }
    if (pos == i) return;
    auto nodePtr = nodes[static_cast<size_t>(i)];
    nodes.erase(nodes.begin() + i);
    nodes.insert(nodes.begin() + pos, nodePtr);
    rebuild();
}

void Game::queueClone(Blk* b, const std::shared_ptr<Node>& source, const std::shared_ptr<Node>& self) {
    if (!source || source == autoload || sceneClones >= MAX_SCENE_CLONES) return;
    auto clone = std::make_shared<Node>(*source);
    // Reset runtime fields
    clone->visualCopies.clear();
    clone->vars.clear();
    clone->par = nullptr;
    clone->inOrder = false;
    clone->pressed = false;
    clone->pid = -1;
    clone->swiped = false;
    clone->swPid = -1;
    clone->tch = -1;

    std::string base = trimStr(b->s("name"));
    if (base.empty() || base == "self") base = source->name + "_copia";
    std::string name = base;
    for (int i = 2; byName(name) != nullptr || pendingCloneNames.count(name); ++i) {
        name = base + "_" + std::to_string(i);
    }
    clone->name = name;
    clone->id = nextId++;
    clone->current = false;
    if (!trimStr(b->s("x")).empty()) clone->x = static_cast<float>(val(b->s("x"), self.get()).asNum());
    if (!trimStr(b->s("y")).empty()) clone->y = static_cast<float>(val(b->s("y"), self.get()).asNum());
    pendingClones.push_back(clone);
    pendingCloneNames.insert(name);
    sceneClones++;
}

void Game::activateClones() {
    if (pendingClones.empty()) return;
    size_t first = nodes.size();
    nodes.insert(nodes.end(), pendingClones.begin(), pendingClones.end());
    pendingClones.clear();
    pendingCloneNames.clear();
    fnMapValid = false;
    swipeState = -1;
    for (size_t i = first; i < nodes.size(); ++i) initRuntimeNode(nodes[i]);
    rebuild();
    compute();
    startNodes(first);
}

std::shared_ptr<Node> Game::captureVisual(const std::shared_ptr<Node>& source) {
    compute();
    auto picture = std::make_shared<Node>();
    picture->type = source->type;
    picture->wx = source->wx;
    picture->wy = source->wy;
    picture->w = source->w;
    picture->h = source->h;
    picture->wa = source->wa;
    picture->wvis = source->wvis;
    picture->rotd = source->rotd;
    if (source->rotd) picture->tm = source->tm;
    picture->res = source->res;
    picture->trimTransparent = source->trimTransparent;
    picture->pressed = source->pressed;
    picture->dim = source->dim;
    picture->color = source->color;
    if (source->type == "AnimatedSprite") {
        picture->type = "Image";
        if (!source->frames.empty()) {
            int idx = std::min(std::max(0, source->frame), static_cast<int>(source->frames.size()) - 1);
            picture->res = source->frames[static_cast<size_t>(idx)];
        } else {
            picture->res = "";
        }
    } else if (source->type == "Button" || source->type == "Label") {
        picture->text = (source->type == "Label") ? labelText(source.get()) : source->text;
        picture->literalText = true;
        picture->font = source->font;
        picture->tsize = source->tsize;
        picture->bold = source->bold;
        picture->italic = source->italic;
        picture->shadow = source->shadow;
        picture->wrap = source->wrap;
        picture->align = source->align;
        picture->valign = source->valign;
        picture->outline = source->outline;
        picture->ocolor = source->ocolor;
    }
    return picture;
}

void Game::addVisualCopy(Blk* block, const std::shared_ptr<Node>& source, const std::shared_ptr<Node>& self) {
    if (!source || visualCopyCount >= MAX_VISUAL_COPIES) return;
    if (source->type != "Image" && source->type != "ImageButton" &&
        source->type != "AnimatedSprite" && source->type != "Color" &&
        source->type != "Button" && source->type != "Label") {
        return;
    }
    float dx = static_cast<float>(val(block->s("x"), self.get()).asNum());
    float dy = static_cast<float>(val(block->s("y"), self.get()).asNum());
    if (!std::isfinite(dx) || !std::isfinite(dy)) return;
    std::shared_ptr<Node> picture = block->b("share") ? nullptr : captureVisual(source);
    source->visualCopies.push_back(VisualCopy{picture, dx, dy});
    visualCopyCount++;
}

void Game::exec(Blk* b, const std::shared_ptr<Node>& self) {
    auto t = tgt(b, self);
    if (b->hasField("node") && !t && b->t != "funcExists") return;

    if (b->t == "send") {
        Msg m;
        m.m = b->s("msg");
        m.from = self;
        std::string sc = b->s("scope");
        m.sc = (sc == "nodo") ? 1 : (sc.rfind("jer", 0) == 0 ? 2 : (sc == "global" ? 3 : 0));
        msgs.push_back(std::move(m));
    } else if (b->t == "var") {
        std::string name = trimStr(b->s("name"));
        if (name.empty()) return;
        std::string type = b->s("vtype");
        Val v = castType(type, val(b->s("val"), self.get()));
        if (persist.count(name)) {
            auto it = storeMap.find(name);
            if (it != storeMap.end()) {
                Val st = decodeSaved(it->second);
                if (!st.isNone()) v = castType(type, st);
            }
        }
        std::string sc = b->s("scope");
        if (sc == "local") {
            Frame* fr = fnFrame();
            if (fr) fr->loc[name] = v;
            else svars[name] = v;
        } else if (sc == "nodo") {
            self->vars[name] = v;
        } else if (sc == "escena") {
            svars[name] = v;
        } else {
            gvars[name] = v;
        }
    } else if (b->t == "setvar") {
        std::string name = trimStr(b->s("name"));
        Val cur = getVar(name, self.get());
        if (!cur.isNone()) putVar(name, castLike(cur, val(b->s("val"), self.get())), self.get());
    } else if (b->t == "math") {
        std::string name = trimStr(b->s("name"));
        Val cur = getVar(name, self.get());
        if (cur.isNone()) cur = Val::makeInt(0);
        Val v = val(b->s("val"), self.get());
        double x = cur.asNum(), y = v.asNum(), r = 0.0;
        std::string op = b->s("op");
        if (op == "-") r = x - y;
        else if (op == "*") r = x * y;
        else if (op == "/") r = (y == 0.0) ? 0.0 : x / y;
        else r = x + y;
        if (cur.kind == Val::INT && v.kind == Val::INT) putVar(name, Val::makeInt(static_cast<int64_t>(r)), self.get());
        else putVar(name, Val::makeFloat(r), self.get());
    } else if (b->t == "enumnext") {
        std::string name = trimStr(b->s("name"));
        Val cur = getVar(name, self.get());
        std::string et = trimStr(b->s("enum"));
        auto eit0 = enums.find(et);
        if (cur.kind != Val::ENUM && eit0 != enums.end() && !eit0->second.empty()) {
            cur = Val::makeEnum(et, eit0->second[0], 0);
        }
        if (cur.kind == Val::ENUM) {
            auto eit = enums.find(cur.e.type);
            if (eit != enums.end() && !eit->second.empty()) {
                const auto& vals = eit->second;
                int sz = static_cast<int>(vals.size());
                std::string dr = b->s("dir");
                int i = cur.e.idx;
                if (dr == "primero") i = 0;
                else if (dr == "último") i = sz - 1;
                else if (dr == "aleatorio") {
                    std::uniform_int_distribution<int> dist(0, sz - 1);
                    i = dist(rnd);
                } else if (dr == "ir a") {
                    Val tv = castEnum(cur.e.type, val(b->s("to"), self.get()));
                    if (tv.kind == Val::ENUM) i = tv.e.idx;
                } else {
                    i = cur.e.idx + (dr == "anterior" ? -1 : 1);
                    if (b->b("wrap")) i = (i % sz + sz) % sz;
                    else i = std::max(0, std::min(sz - 1, i));
                }
                putVar(name, Val::makeEnum(cur.e.type, vals[static_cast<size_t>(i)], i), self.get());
            }
        }
    } else if (b->t == "save") {
        for (const auto& nm : splitComma(b->s("names"))) {
            Val v = getVar(nm, self.get());
            if (!v.isNone()) storeMap[nm] = v.encode();
        }
        saveStore();
    } else if (b->t == "clearsave") {
        clearStore();
    } else if (b->t == "scene") {
        if (sceneByName(b->s("scene"))) pendingScene = b->s("scene");
    } else if (b->t == "orientset") {
        setOrient(b->s("mode") != "vertical");
    } else if (b->t == "viewmode") {
        viewMode = (b->s("mode") == "viewport") ? "viewport" : "camera";
        camSnap = true;
    } else if (b->t == "camset") {
        if (t && t->type == "Camera2D") {
            std::string what = b->s("what");
            if (what == "zoom") t->zoom = std::max(0.05f, static_cast<float>(val(b->s("val"), self.get()).asNum()));
            else if (what == "seguir a") t->follow = trimStr(b->s("val"));
            else if (what == "suavizado") t->smooth = std::max(0.f, static_cast<float>(val(b->s("val"), self.get()).asNum()));
            else if (what == "activar") { cam = t; camSnap = true; }
        }
    } else if (b->t == "clone") {
        queueClone(b, t, self);
    } else if (b->t == "visualClone") {
        addVisualCopy(b, t, self);
    } else if (b->t == "clearVisualClones") {
        if (t && !t->visualCopies.empty()) {
            visualCopyCount -= static_cast<int>(t->visualCopies.size());
            t->visualCopies.clear();
        }
    } else if (b->t == "funcExists") {
        std::string result = trimStr(b->s("ret"));
        if (!result.empty()) {
            bool exists = findFn(trimStr(b->s("name")), t, true).first != nullptr;
            putVar(result, Val::makeBool(exists), self.get());
        }
    } else if (b->t == "collisionEnable") {
        if (t && t->type == "Collision") t->collisionEnabled = b->b("enabled");
    } else if (b->t == "setimg") {
        t->res = b->s("asset");
        if (t->fit) fitNode(t.get());
        constrain(t.get());
    } else if (b->t == "setsnd") {
        t->snd = b->s("asset");
    } else if (b->t == "play") {
        std::string loopStr = b->s("loop");
        if (loopStr == "activar" || loopStr == "desactivar") t->loop = (loopStr == "activar");
        bool reverse = (b->s("dir") == "reversa");
        bool restart = b->b("restart");
        if (t->type == "Sound") {
            res.playSound(t->id, t->snd, t->loop, t->vol);
        } else if (t->type == "AnimatedSprite") {
            if (restart || (reverse && t->frame == 0)) {
                t->frame = reverse ? std::max(0, static_cast<int>(t->frames.size()) - 1) : 0;
            }
            if (restart || t->backwards != reverse) t->ft = 0.f;
            t->backwards = reverse;
            t->playing = true;
        }
    } else if (b->t == "stop") {
        if (t->type == "Sound") res.stopSound(t->id);
        else if (t->type == "AnimatedSprite") t->playing = false;
    } else if (b->t == "breakloop") {
        if (t->type == "AnimatedSprite") t->loop = false;
    } else if (b->t == "quit") {
        autoSave();
        running = false;
        if (onQuit) onQuit();
    } else if (b->t == "mod") {
        t->alpha = clamp01(static_cast<float>(val(b->s("val"), self.get()).asNum()));
        if (b->hasField("touch")) t->touchAlways = b->b("touch");
    } else if (b->t == "layer") {
        layerOps.push_back({t, b->s("mode")});
    } else if (b->t == "vis") {
        t->visible = val(b->s("val"), self.get()).asTruthy();
    } else if (b->t == "stoprender") {
        t->norender = true;
        dropRes(t.get());
    } else if (b->t == "startrender") {
        t->norender = false;
    } else if (b->t == "pos") {
        if (!trimStr(b->s("x")).empty()) t->x = static_cast<float>(val(b->s("x"), self.get()).asNum());
        if (!trimStr(b->s("y")).empty()) t->y = static_cast<float>(val(b->s("y"), self.get()).asNum());
        constrain(t.get());
    } else if (b->t == "text") {
        t->text = val(b->s("val"), self.get()).asString();
    } else if (b->t == "tween") {
        Tw w;
        w.n = t;
        w.d = static_cast<float>(val(b->s("secs"), self.get()).asNum());
        w.easing = b->s("ease");
        std::string what = b->s("what");
        if (what == "modulate") {
            w.kind = 0;
            w.from[0] = t->alpha;
            w.to[0] = clamp01(static_cast<float>(val(b->s("x"), self.get()).asNum()));
        } else if (what == "rotación") {
            if (trimStr(b->s("x")).empty()) return;
            w.kind = 3;
            w.from[0] = t->rot;
            w.to[0] = static_cast<float>(val(b->s("x"), self.get()).asNum());
        } else {
            bool mv = (what == "mover");
            w.kind = mv ? 1 : 2;
            w.from[0] = mv ? t->x : t->w;
            w.from[1] = mv ? t->y : t->h;
            w.has[0] = !trimStr(b->s("x")).empty();
            w.has[1] = !trimStr(b->s("y")).empty();
            w.to[0] = static_cast<float>(val(b->s("x"), self.get()).asNum());
            w.to[1] = static_cast<float>(val(b->s("y"), self.get()).asNum());
        }
        for (int i = static_cast<int>(tws.size()) - 1; i >= 0; --i) {
            if (tws[static_cast<size_t>(i)].n == t && tws[static_cast<size_t>(i)].kind == w.kind) {
                tws.erase(tws.begin() + i);
            }
        }
        if (what == "modulate" && b->hasField("touch")) t->touchAlways = b->b("touch");
        if (w.d <= 0.f) applyTween(w, 1.f);
        else tws.push_back(std::move(w));
    }
}

void Game::push(Task* t, const std::vector<std::shared_ptr<Blk>>* list, int kind, int rem, Blk* blk) {
    Frame f;
    f.list = list;
    f.kind = kind;
    f.rem = rem;
    f.blk = blk;
    t->stack.push_back(std::move(f));
}

std::shared_ptr<Task> Game::spawn(const std::vector<std::shared_ptr<Blk>>& list, const std::shared_ptr<Node>& self) {
    auto t = std::make_shared<Task>();
    t->self = self;
    t->fresh = true;
    push(t.get(), &list, 0, 0, nullptr);
    tasks.push_back(t);
    step(t.get());
    return t;
}

void Game::step(Task* t) {
    Task* pv = curTask;
    curTask = t;
    step0(t);
    curTask = pv;
}

Frame Game::pop(Task* t) {
    Frame f = std::move(t->stack.back());
    t->stack.pop_back();
    if (f.fn) {
        t->self = f.prev;
        t->fns--;
    }
    return f;
}

Frame& Game::pushFn(Task* t, Blk* fb, std::unordered_map<std::string, Val> loc,
                    const std::shared_ptr<Node>& prev, const std::string& rv) {
    push(t, &fb->body, 0, 0, nullptr);
    Frame& f = t->stack.back();
    f.fn = true;
    f.loc = std::move(loc);
    f.prev = prev;
    f.rv = rv;
    t->fns++;
    return f;
}

std::pair<Blk*, std::shared_ptr<Node>> Game::findFn(const std::string& name, const std::shared_ptr<Node>& self, bool exact) {
    if (name.empty() || (exact && !self)) return {nullptr, nullptr};
    if (!fnMapValid) {
        fnMap.clear();
        for (const auto& n : nodes) {
            for (const auto& e : n->script) {
                std::string k = (e->t == "func") ? trimStr(e->s("name")) : "";
                if (k.empty()) continue;
                fnMap[k].push_back({e.get(), n});
            }
        }
        fnMapValid = true;
    }
    auto it = fnMap.find(name);
    if (it == fnMap.end() || it->second.empty()) return {nullptr, nullptr};
    for (const auto& o : it->second) {
        if (o.second == self) return o;
    }
    return exact ? std::make_pair<Blk*, std::shared_ptr<Node>>(nullptr, nullptr) : it->second[0];
}

std::unordered_map<std::string, Val> Game::bindFn(Blk* fb, Blk* call, Node* caller) {
    std::unordered_map<std::string, Val> loc;
    auto ps = splitArgs(fb->s("params"));
    auto as = splitArgs(call->s("args"));
    for (size_t i = 0; i < ps.size(); ++i) {
        if (ps[i].empty()) continue;
        loc[ps[i]] = (i < as.size() && !as[i].empty()) ? val(as[i], caller) : Val::makeInt(0);
    }
    return loc;
}

bool Game::moveNode(Node* n, Blk* b, float d, Node* self) {
    float ux = 1.f, uy = 0.f;
    std::string dir = b->s("dir");
    if (dir == "izquierda") { ux = -1.f; uy = 0.f; }
    else if (dir == "arriba") { ux = 0.f; uy = -1.f; }
    else if (dir == "abajo") { ux = 0.f; uy = 1.f; }
    else if (dir == "ángulo") {
        double a = val(b->s("ang"), self).asNum() * 3.14159265358979323846 / 180.0;
        ux = static_cast<float>(std::cos(a));
        uy = static_cast<float>(std::sin(a));
    }
    n->x += ux * d;
    n->y += uy * d;
    bool anti = b->b("anti");
    if (n->par && n->par->type == "Limiter") return constrain(n) && anti;
    if (!anti && !b->b("edge")) return false;
    float ox_ = n->x, oy_ = n->y;
    float mw = n->par ? n->par->w : static_cast<float>(vw);
    float mh = n->par ? n->par->h : static_cast<float>(vh);
    n->x = std::max(0.f, std::min(n->x, mw - n->w));
    n->y = std::max(0.f, std::min(n->y, mh - n->h));
    return anti && (n->x != ox_ || n->y != oy_);
}

int Game::moveStep(Task* t, Blk* b) {
    auto n = tgt(b, t->self);
    if (!n) return 1;
    bool sp = (b->t == "speed"), rt = (b->t == "rotate");
    bool to = rt && (b->s("mode").rfind("ir", 0) == 0);
    bool inf = sp && b->b("inf");
    float secs = static_cast<float>(val(b->s("secs"), t->self.get()).asNum());
    auto it = t->tm.find(b);
    if (it == t->tm.end()) {
        if (secs <= 0.f && !sp) {
            float d = static_cast<float>(val(b->s(rt ? "deg" : "dist"), t->self.get()).asNum());
            if (rt) {
                n->rot = to ? d : n->rot + d;
                constrain(n.get());
            } else {
                moveNode(n.get(), b, d, t->self.get());
            }
            return 1;
        }
        if (inf && !infRun.insert(b).second) return 1;
        t->tm[b] = {0.f, n->rot};
        it = t->tm.find(b);
    }
    std::string cx = sp ? trimStr(b->s("expr")) : "";
    bool end = !cx.empty() && !evalExpr(b, t->self.get());
    if (!end) {
        float el0 = it->second.first;
        float r0 = it->second.second;
        it->second.first += curDt;
        float el1 = it->second.first;
        if (sp) {
            float v0 = static_cast<float>(val(b->s("v0"), t->self.get()).asNum());
            float v1 = static_cast<float>(val(b->s("v1"), t->self.get()).asNum());
            float ramp = static_cast<float>(val(b->s("ramp"), t->self.get()).asNum());
            float va = ramp > 0.f ? v0 + (v1 - v0) * std::min(1.f, el0 / ramp) : v1;
            float vb = ramp > 0.f ? v0 + (v1 - v0) * std::min(1.f, el1 / ramp) : v1;
            end = moveNode(n.get(), b, (va + vb) / 2.f * curDt, t->self.get()) ||
                  (!inf && (secs > 0.f ? el1 >= secs : cx.empty() && el1 >= std::max(ramp, 1.f)));
        } else if (rt) {
            float p1 = std::min(1.f, el1 / secs);
            float d = static_cast<float>(val(b->s("deg"), t->self.get()).asNum());
            n->rot = to ? (r0 + (d - r0) * p1) : (r0 + d * p1);
            constrain(n.get());
            end = (p1 >= 1.f);
        } else {
            float p0 = std::min(1.f, el0 / secs);
            float p1 = std::min(1.f, el1 / secs);
            float dist = static_cast<float>(val(b->s("dist"), t->self.get()).asNum());
            end = moveNode(n.get(), b, dist * (p1 - p0), t->self.get()) || (p1 >= 1.f);
        }
    }
    if (end) {
        t->tm.erase(b);
        if (inf) infRun.erase(b);
    }
    return end ? 1 : 2;
}

bool Game::caseHit(const Val& v, const std::string& list, Node* self) {
    for (const auto& p : splitComma(list)) {
        if (cmp(v, "=", val(p, self))) return true;
    }
    return false;
}

void Game::step0(Task* t) {
    int budget = 3000;
    while (!t->done && t->wait <= 0.f && budget-- > 0) {
        if (t->stack.empty()) {
            t->done = true;
            break;
        }
        Frame& f = t->stack.back();
        if (f.idx >= f.list->size()) {
            bool again = false;
            if (f.kind == 1) {
                f.rem--;
                again = (f.rem > 0);
            } else if (f.kind == 2) {
                again = true;
            } else if (f.kind == 3) {
                again = cond(f.blk, t->self.get());
            }
            if (again) {
                f.idx = 0;
                f.chain = false;
            } else {
                pop(t);
            }
            continue;
        }
        Blk* b = (*f.list)[f.idx++].get();
        if (b->t == "if") {
            int st = condState(t, b);
            if (st == 2) {
                f.idx--;
                t->wait = 0.0001f;
                break;
            }
            f.chain = (st == 1);
            if (f.chain) push(t, &b->body, 0, 0, nullptr);
        } else if (b->t == "elif") {
            if (!f.chain) {
                int st = condState(t, b);
                if (st == 2) {
                    f.idx--;
                    t->wait = 0.0001f;
                    break;
                }
                if (st == 1) {
                    f.chain = true;
                    push(t, &b->body, 0, 0, nullptr);
                }
            }
        } else if (b->t == "breakrep") {
            int k = -1;
            for (int q = static_cast<int>(t->stack.size()) - 1; q >= 0 && !t->stack[static_cast<size_t>(q)].fn; --q) {
                if (t->stack[static_cast<size_t>(q)].kind == 1) {
                    k = q;
                    break;
                }
            }
            if (k >= 0) while (static_cast<int>(t->stack.size()) > k) pop(t);
        } else if (b->t == "else") {
            if (!f.chain) push(t, &b->body, 0, 0, nullptr);
            f.chain = true;
        } else if (b->t == "match") {
            Val mv = val(b->s("val"), t->self.get());
            const std::vector<std::shared_ptr<Blk>>* pick = nullptr;
            const std::vector<std::shared_ptr<Blk>>* def = nullptr;
            for (const auto& c : b->body) {
                if (c->t == "case") {
                    if (!pick && caseHit(mv, c->s("val"), t->self.get())) pick = &c->body;
                } else if (c->t == "default") {
                    def = &c->body;
                }
            }
            if (!pick) pick = def;
            if (pick) push(t, pick, 0, 0, nullptr);
        } else if (b->t == "call" || b->t == "callNode") {
            bool exact = (b->t == "callNode");
            auto fd = findFn(trimStr(b->s("name")), exact ? tgt(b, t->self) : t->self, exact);
            if (!fd.first || t->stack.size() > 40) continue;
            Blk* fb = fd.first;
            auto fnode = fd.second;
            auto loc = bindFn(fb, b, t->self.get());
            if (b->b("par")) {
                auto nt = std::make_shared<Task>();
                nt->self = fnode;
                nt->fresh = true;
                pushFn(nt.get(), fb, std::move(loc), fnode, "");
                tasks.push_back(nt);
                step(nt.get());
            } else {
                pushFn(t, fb, std::move(loc), t->self, trimStr(b->s("ret")));
                t->self = fnode;
            }
        } else if (b->t == "return") {
            bool hasVal = !trimStr(b->s("val")).empty();
            Val rv = hasVal ? val(b->s("val"), t->self.get()) : Val::makeNone();
            while (!t->stack.empty()) {
                Frame x = pop(t);
                if (x.fn) {
                    if (hasVal && !x.rv.empty()) putVar(x.rv, rv, t->self.get());
                    break;
                }
            }
            if (hasVal) svars["retorno"] = rv;
        } else if (b->t == "advance" || b->t == "speed" || b->t == "rotate") {
            if (moveStep(t, b) == 2) {
                f.idx--;
                t->wait = 0.0001f;
            }
        } else if (b->t == "wait") {
            t->wait = std::max(0.0001f, static_cast<float>(val(b->s("secs"), t->self.get()).asNum()) + t->carry);
            t->carry = 0.f;
            t->wk = true;
        } else if (b->t == "repeat") {
            int n = static_cast<int>(val(b->s("n"), t->self.get()).asNum());
            if (n > 0) push(t, &b->body, 1, n, b);
        } else if (b->t == "forever") {
            push(t, &b->body, 2, 0, b);
        } else if (b->t == "while") {
            if (cond(b, t->self.get())) push(t, &b->body, 3, 0, b);
        } else if (b->t == "break") {
            while (!t->stack.empty()) {
                Frame x = pop(t);
                if (x.kind != 0 || x.fn) break;
            }
        } else {
            exec(b, t->self);
            if (!t->stack.empty()) t->stack.back().chain = false;
            if (!pendingScene.empty()) t->done = true;
        }
    }
    if (!t->done && t->wait <= 0.f && budget <= 0) t->wait = 0.0001f;
}

static bool underNode(const Node* n, const Node* root) {
    for (const Node* p = n; p != nullptr; p = p->par) {
        if (p == root) return true;
    }
    return false;
}

void Game::pump() {
    int guard = 0;
    while (!msgs.empty() && guard++ < 500) {
        Msg m = std::move(msgs.front());
        msgs.pop_front();
        std::string nm = trimStr(m.m);
        if (m.sc == 3 && !pendingScene.empty()) gq.push_back(m);
        for (size_t ni = 0; ni < nodes.size(); ++ni) {
            auto n = nodes[ni];
            if (m.from && ((m.sc == 1 && n != m.from) || (m.sc == 2 && !underNode(n.get(), m.from.get())))) {
                continue;
            }
            for (size_t bi = 0; bi < n->script.size(); ++bi) {
                auto& b = n->script[bi];
                if (b->t == "recv" && trimStr(b->s("msg")) == nm) {
                    spawn(b->body, n);
                }
            }
        }
    }
}

// ================= Scene Lifecycle =================
void Game::collectBlkAssets(const std::vector<std::shared_ptr<Blk>>& list,
                            std::unordered_set<std::string>& images,
                            std::unordered_set<std::string>& audio) const {
    for (const auto& b : list) {
        std::string a = b->s("asset");
        if (!a.empty()) {
            if (b->t == "setimg") images.insert(a);
            else if (b->t == "setsnd") audio.insert(a);
        }
        collectBlkAssets(b->body, images, audio);
        collectBlkAssets(b->els, images, audio);
    }
}

void Game::collectSceneAssets(const std::vector<std::shared_ptr<Node>>& scNodes,
                                std::unordered_set<std::string>& images,
                                std::unordered_set<std::string>& audio) const {
    for (const auto& n : scNodes) {
        if (!n->res.empty() && n->type != "Video") images.insert(n->res);
        if (!n->snd.empty()) audio.insert(n->snd);
        for (const auto& fr : n->frames) {
            if (!fr.empty()) images.insert(fr);
        }
        collectBlkAssets(n->script, images, audio);
    }
}

void Game::start() {
    loadStore();
    gvars.clear();
    autoloadStarted = false;
    autoload->vars.clear();
    Scene* s = sceneById(startId);
    if (!s && !scenes.empty()) s = &scenes[0];
    if (s) enterScene(s);
}

void Game::enterScene(Scene* sc) {
    swipeState = -1;
    res.releaseAllAudio();
    curScene = sc;
    if (sc->hasRawNodes) {
        nodes = parseNodes(sc->rawNodes);
    } else {
        nodes.clear();
        for (const auto& n : sc->nodes) nodes.push_back(std::make_shared<Node>(*n));
    }
    if (!autoload->script.empty()) nodes.insert(nodes.begin(), autoload);

    // Per-scene resource loading + texture reuse
    std::unordered_set<std::string> neededImages, neededAudio;
    collectSceneAssets(nodes, neededImages, neededAudio);
    res.prepareScene(neededImages, neededAudio);
    res.preloadSceneImages(neededImages);

    pendingClones.clear();
    pendingCloneNames.clear();
    sceneClones = 0;
    visualCopyCount = 0;
    upAll();
    svars.clear();
    msgs.clear();
    for (auto& m : gq) {
        m.from.reset();
        msgs.push_back(std::move(m));
    }
    gq.clear();
    tclk.clear();
    fnMapValid = false;
    fnMap.clear();
    grabs.clear();
    infRun.clear();
    tws.clear();
    holds.clear();
    layerOps.clear();
    tasks.clear();
    pendingScene.clear();

    rebuild();
    nameMap.clear();
    hasPoll = false;
    hasCollisionEvents = false;
    cam.reset();
    camSnap = true;
    sceneSerial++;

    for (auto& n : nodes) {
        nextId = std::max(nextId, n->id + 1);
        initRuntimeNode(n);
    }
    compute();
    startNodes(0);
    pump();
}

void Game::initRuntimeNode(const std::shared_ptr<Node>& n) {
    if (n != autoload || nameMap.find(n->name) == nameMap.end()) nameMap[n->name] = n;
    n->playing = n->autoplay;
    n->backwards = false;
    n->visualCopies.clear();
    n->rot0 = n->rot;
    n->norender = false;
    n->frame = 0;
    n->ft = 0.f;
    n->pressed = false;
    n->pid = -1;
    if (n->type == "Camera2D" && (!cam || (n->current && !cam->current))) cam = n;
    for (auto& b : n->script) {
        b->hasLast = false;
        b->last.clear();
        b->acc = 0.f;
        b->task.reset();
        b->overlapping = false;
        if (b->t == "frame" || b->t == "change" || b->t == "every") hasPoll = true;
        if (n->type == "Collision" && collisionEvent(b->t)) hasCollisionEvents = true;
    }
}

void Game::startNodes(size_t first) {
    for (size_t i = first; i < nodes.size(); ++i) {
        auto& n = nodes[i];
        if (n->type == "Grab" && !n->anchor.empty()) {
            grabTarget(n.get())->anchor = n->anchor;
        }
    }
    for (size_t i = first; i < nodes.size(); ++i) {
        auto& n = nodes[i];
        if (n->type == "Sound" && n->autoplay) {
            res.playSound(n->id, n->snd, n->loop, n->vol);
        }
    }
    for (size_t i = first; i < nodes.size() && pendingScene.empty(); ++i) {
        auto& n = nodes[i];
        if (n == autoload) {
            if (autoloadStarted) continue;
            autoloadStarted = true;
        }
        for (size_t bi = 0; bi < n->script.size(); ++bi) {
            auto& b = n->script[bi];
            if (b->t == "start") spawn(b->body, n);
            if (!pendingScene.empty()) break;
        }
    }
}

bool Game::active() const {
    if (hasPoll || hasCollisionEvents || !pendingClones.empty() || !pendingScene.empty() ||
        orientChanged || useCam) return true;
    if (!tasks.empty() || !tws.empty() || !holds.empty() || !msgs.empty() || !grabs.empty()) return true;
    for (const auto& n : nodes) {
        if (n->playing && n->type == "AnimatedSprite" && !n->frames.empty()) return true;
    }
    return false;
}

void Game::animate(float dt) {
    for (auto& n : nodes) {
        if (n->type != "AnimatedSprite" || n->frames.empty() || n->norender || !n->playing) continue;
        float spf = 1.f / std::max(0.1f, std::min(240.f, n->fps));
        n->ft += dt;
        while (n->ft >= spf) {
            n->ft -= spf;
            n->frame += n->backwards ? -1 : 1;
            int cnt = static_cast<int>(n->frames.size());
            if (n->frame < 0 || n->frame >= cnt) {
                if (n->loop) {
                    n->frame = n->backwards ? (cnt - 1) : 0;
                } else {
                    n->frame = n->backwards ? 0 : (cnt - 1);
                    n->playing = false;
                    n->ft = 0.f;
                    break;
                }
            }
        }
    }
}

void Game::updateCamera(float dt) {
    useCam = (viewMode == "camera" && cam != nullptr);
    if (!useCam) return;
    compute();
    float tx = cam->wx + cam->w / 2.f, ty = cam->wy + cam->h / 2.f;
    if (!cam->follow.empty()) {
        auto f = byName(cam->follow);
        if (f) {
            tx = f->wx + f->w / 2.f;
            ty = f->wy + f->h / 2.f;
        }
    }
    if (camSnap || cam->smooth <= 0.f) {
        camX = tx;
        camY = ty;
        camSnap = false;
    } else {
        float k = 1.f - std::exp(-cam->smooth * dt);
        camX += (tx - camX) * k;
        camY += (ty - camY) * k;
    }
    camZ = std::max(0.05f, cam->zoom);
    if (cam->lim) {
        float hw = vw / (2.f * camZ), hh = vh / (2.f * camZ);
        camX = (cam->lr - cam->ll < 2 * hw) ? (cam->ll + cam->lr) / 2.f
                                            : std::max(cam->ll + hw, std::min(cam->lr - hw, camX));
        camY = (cam->lb - cam->lt < 2 * hh) ? (cam->lt + cam->lb) / 2.f
                                            : std::max(cam->lt + hh, std::min(cam->lb - hh, camY));
    }
}

void Game::update(float dt) {
    if (dt > 0.1f) dt = 0.1f;
    animate(dt);
    curDt = dt;
    clk += dt;
    if (pendingScene.empty()) activateClones();
    if (!layerOps.empty()) {
        for (auto& o : layerOps) layer(o.first, o.second);
        layerOps.clear();
    }
    for (int i = static_cast<int>(tws.size()) - 1; i >= 0; --i) {
        Tw& t = tws[static_cast<size_t>(i)];
        t.t += dt;
        float k = (t.d <= 0.f) ? 1.f : std::min(1.f, t.t / t.d);
        applyTween(t, k);
        if (k >= 1.f) tws.erase(tws.begin() + i);
    }
    for (int i = static_cast<int>(holds.size()) - 1; i >= 0; --i) {
        Hold& h = holds[static_cast<size_t>(i)];
        if (h.n->pid == h.pid) {
            if (!h.task || h.task->done) h.task = spawn(h.b->body, h.n);
        } else {
            if (h.task) h.task->done = true;
            spawn(h.b->els, h.n);
            holds.erase(holds.begin() + i);
        }
    }
    if (hasPoll) {
        for (size_t a = 0; a < nodes.size(); ++a) {
            auto n = nodes[a];
            for (size_t i = 0; i < n->script.size(); ++i) {
                Blk* b = n->script[i].get();
                if (b->t == "frame") {
                    if (!b->task || b->task->done) b->task = spawn(b->body, n);
                } else if (b->t == "every") {
                    double iv = val(b->s("secs"), n.get()).asNum();
                    b->acc += dt;
                    if (iv > 0.0001 && b->acc >= iv) {
                        b->acc = 0.f;
                        if (!b->task || b->task->done) b->task = spawn(b->body, n);
                    }
                } else if (b->t == "change") {
                    auto t = tgt(b, n);
                    if (t) {
                        std::string cur = prop(t.get(), b->s("prop")).asString();
                        if (!b->hasLast) {
                            b->hasLast = true;
                            b->last = cur;
                        } else if (cur != b->last) {
                            b->last = cur;
                            spawn(b->body, n);
                        }
                    }
                }
            }
        }
    }
    for (size_t i = 0; i < tasks.size(); ++i) {
        auto t = tasks[i];
        if (t->done) continue;
        if (t->fresh) {
            t->fresh = false;
            continue;
        }
        if (t->wait > 0.f) {
            t->wait -= dt;
            if (t->wait > 0.f) continue;
            if (t->wk) {
                t->carry = std::max(t->wait, -0.1f);
                t->wk = false;
            }
            t->wait = 0.f;
        }
        step(t.get());
    }
    tasks.erase(std::remove_if(tasks.begin(), tasks.end(),
                               [](const std::shared_ptr<Task>& t) { return t->done; }),
                tasks.end());
    if (hasCollisionEvents && pendingScene.empty()) pollCollisions();
    pump();
    if (orientChanged) {
        orientChanged = false;
        for (size_t a = 0; a < nodes.size(); ++a) {
            auto n = nodes[a];
            for (size_t i = 0; i < n->script.size(); ++i) {
                if (n->script[i]->t == "orient") spawn(n->script[i]->body, n);
            }
        }
    }
    if (!pendingScene.empty()) {
        std::string nm = pendingScene;
        pendingScene.clear();
        Scene* sc = sceneByName(nm);
        if (sc) enterScene(sc);
    }
    updateCamera(dt);
}

// ================= Rendering =================
void Game::computeMap(int sw, int sh) {
    lastScreenW = sw;
    lastScreenH = sh;
    float ax = static_cast<float>(sw) / static_cast<float>(vw);
    float ay = static_cast<float>(sh) / static_cast<float>(vh);
    if (stretch == "stretch") {
        sx = ax;
        sy = ay;
    } else {
        float s = (stretch == "fill") ? std::max(ax, ay) : std::min(ax, ay);
        sx = s;
        sy = s;
    }
    ox = (sw - vw * sx) / 2.f;
    oy = (sh - vh * sy) / 2.f;
}

std::string Game::labelText(Node* n) {
    std::string t = n->text;
    if (n->literalText || t.find('{') == std::string::npos) return t;
    std::string out;
    size_t i = 0;
    while (i < t.size()) {
        if (t[i] == '{') {
            size_t j = t.find('}', i);
            if (j != std::string::npos && j > i) {
                std::string nm = trimStr(t.substr(i + 1, j - i - 1));
                Val v = getVar(nm, n);
                if (v.isNone() && nm.find('.') != std::string::npos) {
                    Val o = val(nm, n);
                    if (o.asString() != nm) v = o;
                }
                out += v.isNone() ? ("{" + nm + "}") : v.asString();
                i = j + 1;
                continue;
            }
        }
        out.push_back(t[i]);
        i++;
    }
    return out;
}

void Game::drawQuad(SDL_Renderer* renderer, SDL_Texture* tex,
                    float x0, float y0, float w0, float h0,
                    float u0, float v0, float u1, float v1,
                    const Mat2D* nodeTm, bool useCamera,
                    uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
    float pts[8] = {
        x0, y0,
        x0 + w0, y0,
        x0 + w0, y0 + h0,
        x0, y0 + h0
    };
    if (nodeTm) nodeTm->mapPoints(pts, 4);
    if (useCamera) {
        for (int i = 0; i < 4; ++i) {
            pts[i * 2] = (pts[i * 2] - camX) * camZ + vw / 2.f;
            pts[i * 2 + 1] = (pts[i * 2 + 1] - camY) * camZ + vh / 2.f;
        }
    }
    for (int i = 0; i < 4; ++i) {
        pts[i * 2] = ox + pts[i * 2] * sx;
        pts[i * 2 + 1] = oy + pts[i * 2 + 1] * sy;
    }

    SDL_FColor col{r / 255.f, g / 255.f, b / 255.f, a / 255.f};
    SDL_Vertex verts[4];
    verts[0] = {{pts[0], pts[1]}, col, {u0, v0}};
    verts[1] = {{pts[2], pts[3]}, col, {u1, v0}};
    verts[2] = {{pts[4], pts[5]}, col, {u1, v1}};
    verts[3] = {{pts[6], pts[7]}, col, {u0, v1}};
    static const int indices[6] = {0, 1, 2, 0, 2, 3};
    SDL_RenderGeometry(renderer, tex, verts, 4, indices, 6);
}

void Game::drawBody(SDL_Renderer* renderer, Node* n, bool cull, bool useCamera) {
    if (!n->wvis || n->wa <= 0.003f) return;
    if (cull && !n->rotd && (n->wx > vw || n->wy > vh || n->wx + n->w < 0 || n->wy + n->h < 0)) return;
    int a = std::max(0, std::min(255, static_cast<int>(n->wa * 255.f)));
    const Mat2D* tm = n->rotd ? &n->tm : nullptr;

    if (n->type == "Image" || n->type == "ImageButton") {
        const TextureEntry* te = res.getTexture(n->res);
        if (!te || !te->tex) return;
        int aa = (n->type == "ImageButton" && n->pressed && n->dim) ? (a * 7 / 10) : a;
        float x0 = n->wx, y0 = n->wy, w0 = n->w, h0 = n->h;
        float u0 = 0.f, v0 = 0.f, u1 = 1.f, v1 = 1.f;
        if (n->trimTransparent && n->type == "Image") {
            const RectI* tr = res.getTrimBounds(n->res);
            if (tr) {
                if (tr->empty()) return;
                float ksX = n->w / static_cast<float>(te->w);
                float ksY = n->h / static_cast<float>(te->h);
                x0 = n->wx + tr->left * ksX;
                y0 = n->wy + tr->top * ksY;
                w0 = tr->width() * ksX;
                h0 = tr->height() * ksY;
                u0 = static_cast<float>(tr->left) / static_cast<float>(te->w);
                v0 = static_cast<float>(tr->top) / static_cast<float>(te->h);
                u1 = static_cast<float>(tr->right) / static_cast<float>(te->w);
                v1 = static_cast<float>(tr->bottom) / static_cast<float>(te->h);
            }
        }
        drawQuad(renderer, te->tex, x0, y0, w0, h0, u0, v0, u1, v1, tm, useCamera, 255, 255, 255, static_cast<uint8_t>(aa));
    } else if (n->type == "AnimatedSprite") {
        if (n->frames.empty()) return;
        int idx = std::min(std::max(0, n->frame), static_cast<int>(n->frames.size()) - 1);
        const TextureEntry* te = res.getTexture(n->frames[static_cast<size_t>(idx)]);
        if (!te || !te->tex) return;
        drawQuad(renderer, te->tex, n->wx, n->wy, n->w, n->h, 0.f, 0.f, 1.f, 1.f, tm, useCamera, 255, 255, 255, static_cast<uint8_t>(a));
    } else if (n->type == "Color") {
        uint8_t ca = static_cast<uint8_t>((((n->color >> 24) & 0xFF) * a) / 255);
        uint8_t cr = (n->color >> 16) & 0xFF;
        uint8_t cg = (n->color >> 8) & 0xFF;
        uint8_t cb = n->color & 0xFF;
        drawQuad(renderer, nullptr, n->wx, n->wy, n->w, n->h, 0.f, 0.f, 1.f, 1.f, tm, useCamera, cr, cg, cb, ca);
    } else if (n->type == "Button") {
        uint8_t bgv = n->pressed ? 0x3A : 0x5A;
        drawQuad(renderer, nullptr, n->wx, n->wy, n->w, n->h, 0.f, 0.f, 1.f, 1.f, tm, useCamera, bgv, bgv, bgv, static_cast<uint8_t>(a));
        std::string key = "BTN|" + n->text + "|" + std::to_string(n->tsize) + "|" + n->font + "|" +
                          (n->bold ? "1" : "0") + (n->italic ? "1" : "0");
        const LabelTexEntry* lt = res.getLabelTexture(key, n->text, n->font, n->tsize,
                                                      n->bold, n->italic, 0xFFFFFFFF,
                                                      1, static_cast<int>(std::round(n->w)),
                                                      false, 0.f, 0xFF000000, false);
        if (lt && lt->tex) {
            float tx = n->wx + (n->w - lt->w) / 2.f;
            float ty = n->wy + (n->h - lt->h) / 2.f;
            drawQuad(renderer, lt->tex, tx, ty, static_cast<float>(lt->w), static_cast<float>(lt->h),
                     0.f, 0.f, 1.f, 1.f, tm, useCamera, 255, 255, 255, static_cast<uint8_t>(a));
        }
    } else if (n->type == "Label") {
        std::string txt = labelText(n);
        int w = std::max(1, static_cast<int>(std::round(n->w)));
        std::string key = txt + "|" + std::to_string(w) + "|" + std::to_string(n->tsize) + "|" +
                          n->font + "|" + (n->bold ? "1" : "0") + (n->italic ? "1" : "0") + "|" +
                          std::to_string(n->align) + "|" + std::to_string(n->color) + "|" +
                          std::to_string(n->outline) + "|" + std::to_string(n->ocolor) + "|" +
                          (n->shadow ? "1" : "0") + (n->wrap ? "1" : "0");
        const LabelTexEntry* lt = res.getLabelTexture(key, txt, n->font, n->tsize,
                                                      n->bold, n->italic, n->color,
                                                      n->align, w, n->wrap,
                                                      n->outline, n->ocolor, n->shadow);
        if (lt && lt->tex) {
            float oy_ = (n->valign == 1) ? (n->h - lt->h) / 2.f : (n->valign == 2 ? n->h - lt->h : 0.f);
            float ox_ = 0.f;
            if (!n->wrap) {
                ox_ = (n->align == 1) ? (n->w - lt->w) / 2.f : (n->align == 2 ? n->w - lt->w : 0.f);
            }
            drawQuad(renderer, lt->tex, n->wx + ox_, n->wy + oy_,
                     static_cast<float>(lt->w), static_cast<float>(lt->h),
                     0.f, 0.f, 1.f, 1.f, tm, useCamera, 255, 255, 255, static_cast<uint8_t>(a));
        }
    }
}

void Game::drawNode(SDL_Renderer* renderer, Node* n, bool cull, bool useCamera) {
    drawBody(renderer, n, cull, useCamera);
}

void Game::drawWithCopies(SDL_Renderer* renderer, Node* n, bool cull, bool useCamera) {
    drawNode(renderer, n, cull, useCamera);
    for (const auto& copy : n->visualCopies) {
        Node* picture = copy.picture ? copy.picture.get() : n;
        if (!picture->wvis || picture->wa <= 0.003f) continue;
        if (cull && !picture->rotd && (picture->wx + copy.dx > vw || picture->wy + copy.dy > vh ||
            picture->wx + copy.dx + picture->w < 0 || picture->wy + copy.dy + picture->h < 0)) {
            continue;
        }
        float oldWx = picture->wx, oldWy = picture->wy;
        Mat2D oldTm = picture->tm;
        picture->wx += copy.dx;
        picture->wy += copy.dy;
        if (picture->rotd) picture->tm.postTranslate(copy.dx, copy.dy);
        drawNode(renderer, picture, false, useCamera);
        picture->wx = oldWx;
        picture->wy = oldWy;
        picture->tm = oldTm;
    }
}

void Game::draw(SDL_Renderer* renderer, int sw, int sh) {
    computeMap(sw, sh);
    SDL_SetRenderClipRect(renderer, nullptr);
    SDL_SetRenderDrawColor(renderer, 0, 0, 0, 255);
    SDL_RenderClear(renderer);

    SDL_Rect vpRect{
        static_cast<int>(std::floor(ox)),
        static_cast<int>(std::floor(oy)),
        static_cast<int>(std::ceil(vw * sx)),
        static_cast<int>(std::ceil(vh * sy))
    };
    SDL_SetRenderClipRect(renderer, &vpRect);

    uint8_t bgr = (bg >> 16) & 0xFF;
    uint8_t bgg = (bg >> 8) & 0xFF;
    uint8_t bgb = bg & 0xFF;
    SDL_SetRenderDrawColor(renderer, bgr, bgg, bgb, 255);
    SDL_FRect fvp{ox, oy, vw * sx, vh * sy};
    SDL_RenderFillRect(renderer, &fvp);

    compute();
    for (size_t i = 0; i < order.size(); ++i) {
        Node* n = order[i].get();
        bool applyCam = useCam && !n->fixed;
        drawWithCopies(renderer, n, !applyCam, applyCam);
    }

    SDL_SetRenderClipRect(renderer, nullptr);
}
