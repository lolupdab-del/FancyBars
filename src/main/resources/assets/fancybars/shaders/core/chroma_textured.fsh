#version 150

// Ported from SkyHanni's textured_chroma.fsh (https://github.com/hannibal002/SkyHanni)

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

vec3 hsb2rgb_smooth(vec3 c) {
    vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    rgb = rgb * rgb * (3.0 - 2.0 * rgb); // Cubic smoothing
    return c.z * mix(vec3(1.0), rgb, c.y);
}

void main() {
    vec4 originalColor = texture(Sampler0, texCoord0) * vertexColor;
    // keep anti-aliased edges (SkyHanni cuts at 0.1, which is too harsh for smooth text)
    if (originalColor.a < 0.004) {
        discard;
    }
    float fragCoord;
    if (forwardDirection == 1) {
        fragCoord = gl_FragCoord.x - gl_FragCoord.y;
    } else {
        fragCoord = gl_FragCoord.x + gl_FragCoord.y;
    }
    float hue = mod((fragCoord / chromaSize) - timeOffset, 1.0);
    fragColor = vec4(hsb2rgb_smooth(vec3(hue, saturation, rgb2b(originalColor.rgb))), originalColor.a);
}
