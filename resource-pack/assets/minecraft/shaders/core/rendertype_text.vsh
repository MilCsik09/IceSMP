#version 330
#define HEIGHT_BIT 13
#define MAX_BIT 10
#define ADD_OFFSET 4095
#define DEFAULT_OFFSET 10
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
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
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    if (pos.y >= ui.y && ProjMat[3].x == -1) {
        int bit = int(pos.y) >> HEIGHT_BIT;
        if (((bit >> MAX_BIT) & 1) == 1) {
            int id = bit - (1 << MAX_BIT);
            pos.x -= 0.5 * ui.x;
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
            pos.x += ui.x;
            pos.z += layer;
            if (!outline && (pos.z == 0 || pos.z == 1000 || pos.z == -90 || pos.z == 2800)) {
                vertexColor = vec4(0);
            }
        }
    }
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    texCoord0 = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
}
