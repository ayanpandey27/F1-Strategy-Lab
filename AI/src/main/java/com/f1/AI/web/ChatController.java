package com.f1.AI.web;

import com.f1.AI.ai.OllamaStatusService;
import com.f1.AI.ai.RaceChatService;
import com.f1.AI.ai.RaceChatService.ChatAnswer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    public record ChatRequest(@NotNull Long raceId, @NotBlank @Size(max = 1000) String question,
                              List<String> driverIds) {
    }

    private final RaceChatService chat;
    private final OllamaStatusService status;

    public ChatController(RaceChatService chat, OllamaStatusService status) {
        this.chat = chat;
        this.status = status;
    }

    @PostMapping("/chat")
    public ChatAnswer ask(@Valid @RequestBody ChatRequest request) {
        return chat.ask(request.raceId(), request.question(), request.driverIds() == null ? List.of() : request.driverIds());
    }

    @GetMapping("/status")
    public OllamaStatusService.Status status() {
        return status.status();
    }
}
