package io.dataease.community.permissions.security;

import io.dataease.result.ResultCode;
import io.dataease.result.ResultMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CommunityExceptionHandler {

    @ExceptionHandler(CommunityForbiddenException.class)
    public ResponseEntity<ResultMessage> forbidden(CommunityForbiddenException exception) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("DE-FORBIDDEN-FLAG", "true");
        ResultMessage body = new ResultMessage(ResultCode.PERMISSION_NO_ACCESS.code(), exception.getMessage());
        return new ResponseEntity<>(body, headers, HttpStatus.FORBIDDEN);
    }
}
