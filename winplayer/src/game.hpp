#pragma once
#include "math2d.hpp"
#include "pak.hpp"
#include "resources.hpp"
#include "../third_party/nlohmann/json.hpp"

#include <SDL3/SDL.h>
#include <cstdint>
#include <deque>
#include <functional>
#include <memory>
#include <random>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

struct EnumVal {
    std::string type;
    std::string name;
    int idx = 0;
};

struct Val {
    enum Kind { NONE, INT, FLOAT, BOOL, STRING, ENUM } kind = NONE;
    int64_t i = 0;
    double f = 0.0;
    bool b = false;
    std::string s;
    EnumVal e;

    static Val makeNone() { return Val{}; }
    static Val makeInt(int64_t v) { Val x; x.kind = INT; x.i = v; return x; }
    static Val makeFloat(double v) { Val x; x.kind = FLOAT; x.f = v; return x; }
    static Val makeBool(bool v) { Val x; x.kind = BOOL; x.b = v; return x; }
    static Val makeString(const std::string& v) { Val x; x.kind = STRING; x.s = v; return x; }
    static Val makeEnum(const std::string& t, const std::string& n, int idx) {
        Val x; x.kind = ENUM; x.e = {t, n, idx}; return x;
    }

    bool isNone() const { return kind == NONE; }
    bool isNumber() const { return kind == INT || kind == FLOAT; }
    double asNum() const;
    bool asTruthy() const;
    std::string asString() const;
    std::string encode() const;
};

struct Task;
struct Node;

struct Blk {
    std::string t;
    std::unordered_map<std::string, std::string> f;
    std::vector<std::shared_ptr<Blk>> body;
    std::vector<std::shared_ptr<Blk>> els;

    // Runtime state per block
    bool hasLast = false;
    std::string last;
    std::string ck, cta;
    bool hasCta = false;
    std::vector<std::string> ctk;
    bool ctkValid = false;
    float acc = 0.f;
    bool overlapping = false;
    std::shared_ptr<Task> task;

    std::string s(const std::string& k) const {
        auto it = f.find(k);
        return it == f.end() ? std::string() : it->second;
    }
    bool b(const std::string& k) const {
        auto it = f.find(k);
        return it != f.end() && it->second == "true";
    }
    bool hasField(const std::string& k) const {
        return f.find(k) != f.end();
    }
};

struct VisualCopy {
    std::shared_ptr<Node> picture; // nullptr = share live state of original
    float dx = 0.f, dy = 0.f;
};

struct Node {
    int id = 0;
    std::string name, type, res, snd, text, follow;
    int parent = -1;
    bool inherit = true, visible = true, loop = false, autoplay = false, fit = true, fixed = false;
    bool current = false, lim = false, dim = true, swiped = false;
    int swPid = -1, tch = -1;
    bool touchAlways = false, smart = false, smartMaj = false, bold = false, italic = false, shadow = false, wrap = true;
    int smartRes = 32, align = 1, valign = 1;
    uint32_t color = 0xFFFFFFFF, ocolor = 0xFF000000;
    float outline = 0.f, vol = 1.f;
    std::string font;

    float x = 0.f, y = 0.f, w = 200.f, h = 200.f, alpha = 1.f, fps = 8.f, tsize = 36.f;
    float zoom = 1.f, smooth = 0.f, ll = 0.f, lt = 0.f, lr = 1280.f, lb = 720.f;
    std::vector<std::string> frames;
    std::vector<std::shared_ptr<Blk>> script;

    // Runtime state
    float wx = 0.f, wy = 0.f, wa = 1.f;
    bool wvis = false, playing = false, preview = false, pressed = false, backwards = false;
    std::vector<VisualCopy> visualCopies;
    bool literalText = false;
    int pid = -1, frame = 0;
    float ft = 0.f;
    std::unordered_map<std::string, Val> vars;

    // Rotation, anchor, grab, limiter, collision
    float rot = 0.f, rot0 = 0.f, limL = -1.f, limR = -1.f;
    std::string anchor, gtarget, gmode = "arrastrar";
    bool norender = false, rotd = false, trimTransparent = false, collisionEnabled = true;
    Mat2D tm;
    float gx = 0.f, gy = 0.f, ga = 0.f, gacc = 0.f, grot = 0.f;
    Node* par = nullptr;
    bool inOrder = false;
};

struct Scene {
    int id = 1;
    std::string name;
    std::vector<std::shared_ptr<Node>> nodes;
    nlohmann::json rawNodes; // lazy runtime parsing
    bool hasRawNodes = false;
};

struct Tw {
    std::shared_ptr<Node> n;
    int kind = 0; // 0=alpha, 1=move, 2=size, 3=rot
    float from[2] = {0.f, 0.f};
    float to[2] = {0.f, 0.f};
    bool has[2] = {false, false};
    float t = 0.f, d = 0.f;
    std::string easing;
    void apply(float k);
};

struct Frame {
    const std::vector<std::shared_ptr<Blk>>* list = nullptr;
    size_t idx = 0;
    int kind = 0; // 0=seq, 1=repeat, 2=forever, 3=while
    int rem = 0;
    bool chain = false;
    bool fn = false;
    Blk* blk = nullptr;
    std::shared_ptr<Node> prev;
    std::unordered_map<std::string, Val> loc;
    std::string rv;
};

struct Msg {
    std::string m;
    std::shared_ptr<Node> from;
    int sc = 0;
};

struct Task {
    std::shared_ptr<Node> self;
    std::vector<Frame> stack;
    float wait = 0.f;
    bool done = false, fresh = false;
    std::unordered_map<Blk*, std::pair<float, float>> tm;
    float carry = 0.f;
    bool wk = false;
    int fns = 0;
};

struct Hold {
    std::shared_ptr<Node> n;
    Blk* b = nullptr;
    int pid = -1;
    std::shared_ptr<Task> task;
};

class Game {
public:
    static constexpr int MAX_SCENE_CLONES = 1024;
    static constexpr int MAX_VISUAL_COPIES = 256;

    std::vector<Scene> scenes;
    Scene* curScene = nullptr;
    std::vector<std::shared_ptr<Node>> nodes;
    std::vector<std::shared_ptr<Node>> order;

    bool landscape = true, autosave = true, leftClickTouch = true;
    std::string stretch = "keep", viewMode = "viewport";
    int vw = 640, vh = 360, vwL = 640, vhL = 360, vwP = 360, vhP = 640;
    uint32_t bg = 0xFF202020;
    int nextId = 1, nextSceneId = 1, startId = 1;
    std::string appName = "Juego";

    float ox = 0.f, oy = 0.f, sx = 1.f, sy = 1.f;
    int lastScreenW = 640, lastScreenH = 360;

    std::shared_ptr<Node> autoload;
    bool autoloadStarted = false;
    int sceneSerial = 0;
    bool running = true;

    PakReader pak;
    ResourceManager res;

    // Save store path
    std::string savePath;
    std::unordered_map<std::string, std::string> storeMap;

    // Host callbacks
    std::function<void(bool)> onOrientationChange;
    std::function<void()> onQuit;

    // Runtime state
    std::unordered_map<std::string, Val> gvars;
    std::unordered_map<std::string, Val> svars;
    std::unordered_map<std::string, std::vector<std::string>> enums;
    std::unordered_set<std::string> persist;
    std::unordered_map<std::string, std::shared_ptr<Node>> nameMap;

    bool hasPoll = false, hasCollisionEvents = false, orientChanged = false, useCam = false, camSnap = true;
    int sceneClones = 0;
    std::vector<std::shared_ptr<Node>> pendingClones;
    std::unordered_set<std::string> pendingCloneNames;
    int visualCopyCount = 0;
    std::string pendingScene;

    std::shared_ptr<Node> cam;
    float camX = 0.f, camY = 0.f, camZ = 1.f;
    Mat2D camM;

    std::deque<Msg> msgs;
    std::vector<Msg> gq;
    float curDt = 0.f;
    double clk = 0.0;
    Task* curTask = nullptr;
    std::vector<std::shared_ptr<Node>> grabs;
    std::unordered_set<Blk*> infRun;
    std::unordered_map<Blk*, float> tclk;
    bool fnMapValid = false;
    std::unordered_map<std::string, std::vector<std::pair<Blk*, std::shared_ptr<Node>>>> fnMap;

    std::vector<Tw> tws;
    std::vector<Hold> holds;
    std::vector<std::shared_ptr<Task>> tasks;
    std::vector<std::pair<std::shared_ptr<Node>, std::string>> layerOps;

    int swipeState = -1;
    float ptx[16] = {};
    float pty[16] = {};
    bool pton[16] = {};

    std::mt19937 rnd{std::random_device{}()};

    // Expression parser temporary state
    std::vector<std::string> ct;
    size_t ci = 0;
    Node* cself = nullptr;
    bool probLive = true;
    bool collisionGeometry = false;
    float mx = 0.f, my = 0.f;

    Game();
    bool loadFromPakOrDir(const std::string& pakPath, const std::string& dirPath);
    bool loadFromJsonString(const std::string& jsonStr);

    void applyOrientation(bool land);
    void start();
    void update(float dt);
    void draw(SDL_Renderer* renderer, int sw, int sh);
    bool active() const;

    void computeMap(int sw, int sh);
    float toVx(float px) const { return (px - ox) / sx; }
    float toVy(float py) const { return (py - oy) / sy; }

    void down(int pid, float vx, float vy);
    void move(int pid, float vx, float vy);
    void up(int pid);
    void upAll();

    // Persistence
    void loadStore();
    void saveStore();
    void autoSave();
    void clearStore();

    // Node helpers
    Scene* sceneById(int id);
    Scene* sceneByName(const std::string& n);
    std::shared_ptr<Node> byId(int id) const;
    std::shared_ptr<Node> byName(const std::string& n) const;
    void rebuild();
    void compute();
    std::shared_ptr<Node> hit(float vx, float vy, bool onlyTouchable);

private:
    static std::shared_ptr<Blk> parseBlk(const nlohmann::json& o);
    static std::vector<std::shared_ptr<Blk>> parseBlkList(const nlohmann::json& a);
    static std::shared_ptr<Node> parseNode(const nlohmann::json& o);
    static std::vector<std::shared_ptr<Node>> parseNodes(const nlohmann::json& a);

    void addOrder(int pid);
    void enterScene(Scene* sc);
    void collectSceneAssets(const std::vector<std::shared_ptr<Node>>& scNodes,
                            std::unordered_set<std::string>& images,
                            std::unordered_set<std::string>& audio) const;
    void collectBlkAssets(const std::vector<std::shared_ptr<Blk>>& list,
                          std::unordered_set<std::string>& images,
                          std::unordered_set<std::string>& audio) const;
    void initRuntimeNode(const std::shared_ptr<Node>& n);
    void startNodes(size_t first);
    void animate(float dt);
    void updateCamera(float dt);

    bool constrain(Node* n);
    static float origin(const Node* n, bool horizontal);
    static bool inheritsPositionFrom(const Node* node, const Node* ancestor);
    static void collisionCorners(const Node* n, float* points);
    static bool separates(const float* a, const float* b);
    bool overlaps(const Node* a, const Node* b) const;
    void pollCollisions();

    bool touchable(Node* n);
    static bool wantsTouch(const std::vector<std::shared_ptr<Blk>>& l);
    static bool scanSwipe(const std::vector<std::shared_ptr<Blk>>& l);
    bool inNode(const Node* n, float px, float py);
    bool fingerIn(const Node* n);
    bool maskHit(const Node* n, float px, float py);
    std::string maskImage(const Node* n) const;

    Node* grabTarget(Node* g) const;
    float grabAngle(Node* g, Node* t) const;
    void grabStart(const std::shared_ptr<Node>& g, float vx, float vy);
    void grabMove(const std::shared_ptr<Node>& g, float vx, float vy);

    // Script & variables
    Val decodeSaved(const std::string& raw) const;
    Val castEnum(const std::string& type, const Val& v) const;
    Val castType(const std::string& type, const Val& v) const;
    Val castLike(const Val& cur, const Val& v) const;
    Frame* fnFrame();
    Val getVar(const std::string& name, Node* self);
    void putVar(const std::string& name, const Val& v, Node* self);
    Val prop(const Node* n, const std::string& pr) const;
    Val val(const std::string& s, Node* self);
    static bool cmp(const Val& l, const std::string& op, const Val& r);

    std::vector<std::string> tokenize(const std::string& s);
    const std::vector<std::string>& toks(Blk* b);
    float argF(const std::string& a);
    float tsecOf(Blk* b, Node* self);
    bool evalExpr(Blk* b, Node* self);
    bool pOr();
    bool pAnd();
    bool pNot();
    bool pCmp();
    const std::string* pk() const;
    Node* collisionNode(const std::string& token);
    bool condCore(Blk* b, Node* self);
    bool probPass(Blk* b);
    bool cond(Blk* b, Node* self);
    int condState(Task* t, Blk* b);

    std::shared_ptr<Node> tgt(Blk* b, const std::shared_ptr<Node>& self);
    void setOrient(bool land);
    void fitNode(Node* n);
    void dropRes(Node* n);
    void exec(Blk* b, const std::shared_ptr<Node>& self);
    void applyTween(Tw& tween, float progress);
    void layer(const std::shared_ptr<Node>& n, const std::string& mode);
    void queueClone(Blk* b, const std::shared_ptr<Node>& source, const std::shared_ptr<Node>& self);
    void activateClones();
    void addVisualCopy(Blk* block, const std::shared_ptr<Node>& source, const std::shared_ptr<Node>& self);
    std::shared_ptr<Node> captureVisual(const std::shared_ptr<Node>& source);

    void push(Task* t, const std::vector<std::shared_ptr<Blk>>* list, int kind, int rem, Blk* blk);
    std::shared_ptr<Task> spawn(const std::vector<std::shared_ptr<Blk>>& list, const std::shared_ptr<Node>& self);
    void step(Task* t);
    void step0(Task* t);
    Frame pop(Task* t);
    Frame& pushFn(Task* t, Blk* fb, std::unordered_map<std::string, Val> loc, const std::shared_ptr<Node>& prev, const std::string& rv);
    std::pair<Blk*, std::shared_ptr<Node>> findFn(const std::string& name, const std::shared_ptr<Node>& self, bool exact);
    std::unordered_map<std::string, Val> bindFn(Blk* fb, Blk* call, Node* caller);
    int moveStep(Task* t, Blk* b);
    bool moveNode(Node* n, Blk* b, float d, Node* self);
    bool caseHit(const Val& v, const std::string& list, Node* self);
    void pump();

    // Rendering helpers
    std::string labelText(Node* n);
    void drawWithCopies(SDL_Renderer* renderer, Node* n, bool cull, bool useCamera);
    void drawNode(SDL_Renderer* renderer, Node* n, bool cull, bool useCamera);
    void drawBody(SDL_Renderer* renderer, Node* n, bool cull, bool useCamera);
    void drawQuad(SDL_Renderer* renderer, SDL_Texture* tex,
                  float x0, float y0, float w0, float h0,
                  float u0, float v0, float u1, float v1,
                  const Mat2D* nodeTm, bool useCamera,
                  uint8_t r, uint8_t g, uint8_t b, uint8_t a);
};
