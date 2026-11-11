#version 150

// Custom names: the name's own gradient colours flowing across the screen like Custom Chroma.
// SLOT (0-15) is set per pipeline and picks this name's palette from the uniforms.

in vec2 texCoord0;
in vec4 vertexColor;

layout(std140) uniform SbclientChromaUniforms {
    float chromaSize;
    float timeOffset;
    float saturation;
    int forwardDirection;
    vec4 enchantA;
    vec4 enchantB;
    vec4 enchantC;
    vec4 ultimateA;
    vec4 ultimateB;
    vec4 ultimateC;
    vec4 nameColors[96];
    // x: number of colours, y: 1 when it moves, z: flow size in pixels, w: speed
    vec4 nameInfo[16];
};

uniform sampler2D Sampler0;

out vec4 fragColor;

float rgb2b(vec3 rgb) {
    return max(max(rgb.r, rgb.g), rgb.b);
}

vec3 palette(float t, int count) {
    if (count < 2) return nameColors[SLOT * 6].rgb;
    // goes first → last → back to first, so every step is between neighbouring colours (wrapping straight from the
    // last colour to the first made a letter flash when they're very different, like black → light blue)
    float u = t < 0.5 ? t * 2.0 : (1.0 - t) * 2.0;
    float s = u * float(count - 1);
    int i = min(int(floor(s)), count - 2);
    float f = clamp(s - float(i), 0.0, 1.0);
    f = f * f * (3.0 - 2.0 * f);
    return mix(nameColors[SLOT * 6 + i].rgb, nameColors[SLOT * 6 + i + 1].rgb, f);
}

void main() {
    vec4 originalColor = texture(Sampler0, texCoord0) * vertexColor;
    if (originalColor.a < 0.004) {
        discard;
    }
    vec4 info = nameInfo[SLOT];
    float fragCoord;
    if (forwardDirection == 1) {
        fragCoord = gl_FragCoord.x - gl_FragCoord.y;
    } else {
        fragCoord = gl_FragCoord.x + gl_FragCoord.y;
    }
    float time = info.y > 0.5 ? timeOffset * info.w : 0.0;
    float t = mod((fragCoord / max(info.z, 1.0)) - time, 1.0);
    // the text's brightness is kept, so drop shadows stay dark
    fragColor = vec4(palette(t, int(info.x + 0.5)) * rgb2b(originalColor.rgb), originalColor.a);
}
