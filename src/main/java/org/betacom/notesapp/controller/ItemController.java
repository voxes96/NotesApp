package org.betacom.notesapp.controller;

import jakarta.validation.Valid;
import org.betacom.notesapp.dto.CreateItemRequest;
import org.betacom.notesapp.dto.ItemResponse;
import org.betacom.notesapp.service.ItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    public ResponseEntity<?> createItem(
            @Valid @RequestBody CreateItemRequest request,
            Authentication authentication) {
        
        String userLogin = authentication.getName();
        try {
            ItemResponse response = itemService.createItem(request, userLogin);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e);
        }
    }

}
