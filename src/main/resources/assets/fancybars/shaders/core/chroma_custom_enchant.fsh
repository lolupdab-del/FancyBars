#version 150

// Custom chroma: your own colours flowing across the screen the same way as SkyHanni's chroma.

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
};

uniform sampler2D Sampler0;

out vec4 fragColor;

float rgb2b(vec3 rgb) {
    return max(max(rgb.r, rgb.g), rgb.b);
}

vec3 palette(float t) {
    float s = t * 3.0;
    float f = fract(s);
    f = f * f * (3.0 - 2.0 * f);
    int i = int(floor(s));
    if (i == 0) return mix(enchantA.rgb, enchantB.rgb, f);
    if (i == 1) return mix(enchantB.rgb, enchantC.rgb, f);
    return mix(enchantC.rgb, enchantA.rgb, f);
}

void main() {
    vec4 originalColor = texture(Sampler0, texCoord0) * vertexColor;
    if (originalColor.a < 0.004) {
        discard;
    }
    float fragCoord;
    if (forwardDirection == 1) {
        fragCoord = gl_FragCoord.x - gl_FragCoord.y;
    } else {
        fragCoord = gl_FragCoord.x + gl_FragCoord.y;
    }
    float t = mod((fragCoord / chromaSize) - timeOffset, 1.0);
    // the text's brightness is kept, so drop shadows stay dark
    fragColor = vec4(palette(t) * rgb2b(originalColor.rgb), originalColor.a);
}
