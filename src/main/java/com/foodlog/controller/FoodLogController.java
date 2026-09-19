package com.foodlog.controller;

import com.foodlog.client.stt.AudioMimeTypes;
import com.foodlog.domain.LogSource;
import com.foodlog.dto.ConfirmRequest;
import com.foodlog.dto.LogResponse;
import com.foodlog.dto.TextLogRequest;
import com.foodlog.exception.ApiException;
import com.foodlog.service.DailySummary;
import com.foodlog.service.FoodLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/logs")
public class FoodLogController {

    private final FoodLogService service;

    public FoodLogController(FoodLogService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LogResponse logText(@Valid @RequestBody TextLogRequest request) {
        return LogResponse.from(service.logText(request.userId(), request.text(), request.loggedAt(), LogSource.TEXT));
    }

    @PostMapping(path = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LogResponse logVoice(@RequestParam @NotBlank String userId,
                                @RequestPart("audio") MultipartFile audio,
                                @RequestParam(required = false) Instant loggedAt) throws IOException {
        if (audio.isEmpty()) {
            throw ApiException.badRequest("Audio file is empty");
        }
        String mime = AudioMimeTypes.resolve(audio.getOriginalFilename(), audio.getContentType());
        return LogResponse.from(service.logVoice(userId, audio.getBytes(), mime, loggedAt));
    }

    @PostMapping("/{logId}/items/{itemIndex}/confirm")
    public LogResponse confirm(@PathVariable String logId, @PathVariable int itemIndex,
                               @Valid @RequestBody ConfirmRequest request) {
        return LogResponse.from(service.confirmItem(logId, itemIndex, request.foodId()));
    }

    @GetMapping
    public List<LogResponse> list(@RequestParam @NotBlank String userId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                  LocalDate date) {
        return service.list(userId, date != null ? date : service.today()).stream().map(LogResponse::from).toList();
    }

    @GetMapping("/summary")
    public DailySummary summary(@RequestParam @NotBlank String userId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                LocalDate date) {
        return service.summary(userId, date != null ? date : service.today());
    }
}
