package com.danasea.backend.shared.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalRequestValidationTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new InputController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void malformedUuidIsClientInputError() throws Exception {
        mvc.perform(get("/request-contract/ids/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void unknownEnumIsClientInputError() throws Exception {
        mvc.perform(get("/request-contract/filter").param("status", "UNRECOGNIZED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void missingRequiredParameterIsClientInputError() throws Exception {
        mvc.perform(get("/request-contract/filter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void malformedJsonIsClientInputError() throws Exception {
        mvc.perform(post("/request-contract/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // Excluded from the application's component scan; only standalone MockMvc uses these routes.
    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    static class InputController {
        @GetMapping("/request-contract/ids/{id}")
        String identifier(@PathVariable("id") UUID id) { return id.toString(); }

        @GetMapping("/request-contract/filter")
        String filter(@RequestParam("status") State state) { return state.name(); }

        @PostMapping("/request-contract/body")
        String body(@RequestBody Input input) { return input.name(); }
    }

    enum State { OPEN, CLOSED }
    record Input(String name) { }
}
