package org.betacom.notesapp.controller;

import jakarta.validation.Valid;
import org.betacom.notesapp.dto.CreateItemRequest;
import org.betacom.notesapp.dto.ItemListResponse;
import org.betacom.notesapp.dto.ItemResponse;
import org.betacom.notesapp.dto.UpdateItemRequest;
import org.betacom.notesapp.dto.UpdateItemResponse;
import org.betacom.notesapp.service.ItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    public ResponseEntity<ItemResponse> createItem(
            @Valid @RequestBody CreateItemRequest request,
            Authentication authentication) {

        String userLogin = authentication.getName();
        ItemResponse response = itemService.createItem(request, userLogin);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ItemListResponse>> getUserItems(Authentication authentication) {
        String userLogin = authentication.getName();
        List<ItemListResponse> items = itemService.getUserItems(userLogin);
        return ResponseEntity.ok(items);
    }

    @PatchMapping(value = "/{id}")
    public ResponseEntity<UpdateItemResponse> updateItem(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateItemRequest request,
            Authentication authentication) {

        String userLogin = authentication.getName();
        UpdateItemResponse response = itemService.updateItem(id, request, userLogin);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteItem(
            @PathVariable("id") UUID id,
            Authentication authentication) {

        String userLogin = authentication.getName();
        itemService.deleteItem(id, userLogin);

        return ResponseEntity.noContent().build();
    }

}
