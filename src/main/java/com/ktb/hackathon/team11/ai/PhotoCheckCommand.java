package com.ktb.hackathon.team11.ai;

public record PhotoCheckCommand(
        String title,
        String instruction,
        String rule,
        PhotoResource photo,
        PhotoResource referencePhoto
) {
    public record PhotoResource(String mimeType, long sizeBytes, String sha256, String url) {}
}
