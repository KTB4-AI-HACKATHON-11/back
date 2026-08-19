package com.ktb.hackathon.team11.ai;

import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class PhotoUnavailableException extends BusinessException {
    private final String field;

    public PhotoUnavailableException(String field) {
        super(ErrorCode.PHOTO_UNAVAILABLE);
        this.field = field;
    }

    public boolean isReferencePhoto() {
        return "referencePhoto".equals(field);
    }
}
