#version 330
#define HEIGHT_BIT 13
#define MAX_BIT 10
#define ADD_OFFSET 4095
#define DEFAULT_OFFSET 10
const float HUD_LAYOUT_SCALES[16] = float[16](0.75, 0.90, 1.00, 1.15, 1.25, 1.40, 1.60, 1.80,
        2.00, 2.20, 2.40, 2.60, 2.80, 3.00, 3.25, 3.50);
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
void main() {
    vec3 pos = Position;
    vec2 ui = ceil(2 / vec2(ProjMat[0][0], -ProjMat[1][1]));
    float responsiveScale = clamp(min(ScreenSize.x / 2560.0, ScreenSize.y / 1440.0), 0.65, 1.5);
    vec2 hudScale = vec2(responsiveScale) * ui / ScreenSize;
    bool hudGlyph = false;
    bool topLeft = false;
    float layoutScale = 1.0;
    float layoutYOffset = 0.0;
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    if (pos.y >= ui.y && ProjMat[3].x == -1) {
        int bit = int(pos.y) >> HEIGHT_BIT;
        if (((bit >> MAX_BIT) & 1) == 1) {
            int id = bit - (1 << MAX_BIT);
            hudGlyph = true;
            topLeft = id >= 11 && id <= 15;
            ivec3 packedColor = ivec3(round(Color.rgb * 255.0));
            int layoutCode = (packedColor.r & 15) | ((packedColor.g & 15) << 4)
                    | ((packedColor.b & 15) << 8) | ((packedColor.b & 16) << 8)
                    | ((packedColor.r & 16) << 9);
            layoutYOffset = float((layoutCode & 1023) - 512);
            layoutScale = HUD_LAYOUT_SCALES[(layoutCode >> 10) & 15];
            vec3 visualColor = vec3((packedColor & ivec3(224, 240, 224))
                    + ivec3(16, 8, 16)) / 255.0;
            vertexColor = vec4(min(visualColor, vec3(1.0)), Color.a)
                    * texelFetch(Sampler2, UV2 / 16, 0);
            pos.y -= (bit << HEIGHT_BIT) + ADD_OFFSET + DEFAULT_OFFSET;
            float layer = 0;
            bool outline = false;
            if (id == 4) layer = 1;
            else if (id == 5) layer = 2;
            else if (id == 6) layer = 3;
            else if (id == 7) layer = 4;
            else if (id == 8) layer = 5;
            else if (id == 9) layer = 6;
            else if (id == 10) { layer = 7; outline = true; }
            else if (id == 11) layer = 1;
            else if (id == 12) layer = 2;
            else if (id == 13) layer = 3;
            else if (id == 14) layer = 4;
            else if (id == 15) { layer = 5; outline = true; }
            pos.z += layer;
            if (!outline && (pos.z == 0 || pos.z == 1000 || pos.z == -90 || pos.z == 2800)) {
                vertexColor = vec4(0);
            }
        }
    }
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    texCoord0 = UV0;
    vec4 clipPosition = ProjMat * ModelViewMat * vec4(pos, 1.0);
    if (hudGlyph) {
        vec2 selectedHudScale = hudScale * layoutScale;
        if (topLeft) {
            clipPosition.x = -clipPosition.w + clipPosition.x * selectedHudScale.x;
            clipPosition.y = clipPosition.w
                    + (clipPosition.y - clipPosition.w) * selectedHudScale.y
                    - layoutYOffset * responsiveScale * layoutScale
                    * 2.0 * clipPosition.w / ScreenSize.y;
        } else {
            clipPosition.x = clipPosition.w + clipPosition.x * selectedHudScale.x;
            clipPosition.y = clipPosition.w
                    + (clipPosition.y - clipPosition.w) * selectedHudScale.y
                    - layoutYOffset * 2.0 * clipPosition.w / ScreenSize.y;
        }
    }
    gl_Position = clipPosition;
}
