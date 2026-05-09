package org.betacom.notesapp.service;

import org.betacom.notesapp.dto.CreateItemRequest;
import org.betacom.notesapp.dto.ItemListResponse;
import org.betacom.notesapp.dto.ItemResponse;
import org.betacom.notesapp.dto.UpdateItemRequest;
import org.betacom.notesapp.dto.UpdateItemResponse;
import org.betacom.notesapp.exception.ForbiddenAccessException;
import org.betacom.notesapp.exception.ItemNotFoundException;
import org.betacom.notesapp.exception.ItemVersionConflictException;
import org.betacom.notesapp.model.Item;
import org.betacom.notesapp.model.PermissionRole;
import org.betacom.notesapp.model.User;
import org.betacom.notesapp.repository.ItemPermissionRepository;
import org.betacom.notesapp.repository.ItemRepository;
import org.betacom.notesapp.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;


@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final ItemPermissionRepository itemPermissionRepository;

    ItemService(ItemRepository itemRepository, UserRepository userRepository, ItemPermissionRepository itemPermissionRepository) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.itemPermissionRepository = itemPermissionRepository;
    }

    public ItemResponse createItem(CreateItemRequest request, String userLogin) {
        User user = userRepository.findByLogin(userLogin)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        Item item = new Item();
        item.setTitle(request.title());
        item.setContent(request.content());
        item.setOwner(user);
        
        Item savedItem = itemRepository.save(item);
        
        return new ItemResponse(
                savedItem.getId(),
                savedItem.getTitle(),
                savedItem.getContent(),
                savedItem.getVersion(),
                savedItem.getOwner().getId(),
                savedItem.getCreatedAt(),
                savedItem.getUpdatedAt()
        );
    }

    public List<ItemListResponse> getUserItems(String userLogin) {
        List<Item> items = itemRepository.findByOwnerLoginAndDeletedFalse(userLogin);
        
        return items.stream()
                .map(item -> new ItemListResponse(
                        item.getId(),
                        item.getTitle(),
                        item.getContent(),
                        item.getVersion(),
                        item.getOwner().getId(),
                        item.getUpdatedAt()
                ))
                .toList();
    }

    public UpdateItemResponse updateItem(UUID id, UpdateItemRequest request, String userLogin) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException("Item not found or has been deleted"));

        checkUpdateItemConstraints(item, request, userLogin);

        if (request.title() != null) {
            item.setTitle(request.title());
        }
        if (request.content() != null) {
            item.setContent(request.content());
        }
        
        Item updatedItem = itemRepository.save(item);
        
        return new UpdateItemResponse(
                updatedItem.getId(),
                updatedItem.getTitle(),
                updatedItem.getContent(),
                updatedItem.getVersion(),
                updatedItem.getUpdatedAt()
        );
    }

    private void checkUpdateItemConstraints(Item item, UpdateItemRequest request, String userLogin) {
        if (item.getDeleted()) {
            throw new ItemNotFoundException("Item not found or has been deleted");
        }

        if (!item.getOwner().getLogin().equals(userLogin) ||
                itemPermissionRepository.existsByItemAndUserLoginAndRole(item, userLogin, PermissionRole.EDITOR)) {
            throw new ForbiddenAccessException("You do not have permission to edit this item");
        }

        if (!item.getVersion().equals(request.version())) {
            throw new ItemVersionConflictException(
                    "Version conflict - the item has been modified by someone else",
                    item.getVersion()
            );
        }
    }

    public void deleteItem(UUID id, String userLogin) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException("Item not found or has been deleted"));

        if (!item.getOwner().getLogin().equals(userLogin)) {
            throw new ForbiddenAccessException("You do not have permission to edit this item");
        }

        item.setDeleted(true);
        itemRepository.save(item);
    }

}
