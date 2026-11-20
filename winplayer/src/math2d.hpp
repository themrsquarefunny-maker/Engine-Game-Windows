#pragma once
#include <cmath>

struct Mat2D {
    // x' = a*x + c*y + tx
    // y' = b*x + d*y + ty
    float a = 1.f, b = 0.f, c = 0.f, d = 1.f, tx = 0.f, ty = 0.f;

    void reset() {
        a = 1.f; b = 0.f;
        c = 0.f; d = 1.f;
        tx = 0.f; ty = 0.f;
    }

    static Mat2D multiply(const Mat2D& m1, const Mat2D& m2) {
        Mat2D r;
        r.a = m1.a * m2.a + m1.c * m2.b;
        r.b = m1.b * m2.a + m1.d * m2.b;
        r.c = m1.a * m2.c + m1.c * m2.d;
        r.d = m1.b * m2.c + m1.d * m2.d;
        r.tx = m1.a * m2.tx + m1.c * m2.ty + m1.tx;
        r.ty = m1.b * m2.tx + m1.d * m2.ty + m1.ty;
        return r;
    }

    void preRotate(float deg, float px, float py) {
        double rad = deg * 3.14159265358979323846 / 180.0;
        float cs = static_cast<float>(std::cos(rad));
        float sn = static_cast<float>(std::sin(rad));
        Mat2D r;
        r.a = cs;  r.c = -sn; r.tx = px - cs * px + sn * py;
        r.b = sn;  r.d = cs;  r.ty = py - sn * px - cs * py;
        *this = multiply(*this, r);
    }

    void preTranslate(float dx, float dy) {
        tx += a * dx + c * dy;
        ty += b * dx + d * dy;
    }

    void postTranslate(float dx, float dy) {
        tx += dx;
        ty += dy;
    }

    void postScale(float sx, float sy) {
        a *= sx; c *= sx; tx *= sx;
        b *= sy; d *= sy; ty *= sy;
    }

    bool invert(Mat2D& out) const {
        float det = a * d - b * c;
        if (std::fabs(det) < 1e-8f) return false;
        float inv = 1.0f / det;
        out.a = d * inv;
        out.b = -b * inv;
        out.c = -c * inv;
        out.d = a * inv;
        out.tx = (c * ty - d * tx) * inv;
        out.ty = (b * tx - a * ty) * inv;
        return true;
    }

    void mapPoint(float& x, float& y) const {
        float nx = a * x + c * y + tx;
        float ny = b * x + d * y + ty;
        x = nx;
        y = ny;
    }

    void mapPoints(float* pts, int count) const {
        for (int i = 0; i < count; ++i) {
            mapPoint(pts[i * 2], pts[i * 2 + 1]);
        }
    }

    void mapVector(float& dx, float& dy) const {
        float nx = a * dx + c * dy;
        float ny = b * dx + d * dy;
        dx = nx;
        dy = ny;
    }
};
