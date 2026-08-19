package com.ktb.hackathon.team11.ai;

public record PhotoCheckCommand(
    String title,
    String instruction,
    String rule,
    String mimeType,
    long sizeBytes,
    String sha256,
    String url) {}
