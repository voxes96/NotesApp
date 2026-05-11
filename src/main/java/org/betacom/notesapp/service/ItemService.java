package org.betacom.notesapp.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.betacom.notesapp.dto.CreateItemRequest;
import org.betacom.notesapp.dto.ItemHistoryResponse;
import org.betacom.notesapp.dto.ItemListResponse;
import org.betacom.notesapp.dto.ItemResponse;
import org.betacom.notesapp.dto.ShareItemRequest;
import org.betacom.notesapp.dto.ShareItemResponse;
import org.betacom.notesapp.dto.UpdateItemRequest;
import org.betacom.notesapp.dto.UpdateItemResponse;
import org.betacom.notesapp.exception.ForbiddenAccessException;
import org.betacom.notesapp.exception.ItemNotFoundException;
import org.betacom.notesapp.exception.ItemVersionConflictException;
import org.betacom.notesapp.model.CustomRevisionEntity;
import org.betacom.notesapp.model.Item;
import org.betacom.notesapp.model.ItemPermission;
import org.betacom.notesapp.model.PermissionRole;
import org.betacom.notesapp.model.User;
import org.betacom.notesapp.repository.ItemPermissionRepository;
import org.betacom.notesapp.repository.ItemRepository;
import org.betacom.notesapp.repository.UserRepository;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final ItemPermissionRepository itemPermissionRepository;

    @PersistenceContext
    private EntityManager entityManager;

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

        if (!(item.getOwner().getLogin().equals(userLogin) ||
                itemPermissionRepository.existsByItemAndUserLoginAndRole(item, userLogin, PermissionRole.EDITOR))) {
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

    public List<ItemHistoryResponse> getItemHistory(UUID itemId, String userLogin) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));

        User user = userRepository.findByLogin(userLogin)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        boolean isOwner = item.getOwner().getId().equals(user.getId());
        boolean hasPermission = itemPermissionRepository.existsByItemIdAndUserId(itemId, user.getId());

        if (!isOwner || !hasPermission) {
            throw new ForbiddenAccessException("You do not have access to this item's history");
        }

        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        List<?> revisions = auditReader.createQuery()
                .forRevisionsOfEntity(Item.class, false, true)
                .add(AuditEntity.id().eq(itemId))
                .getResultList();

        List<ItemHistoryResponse> history = new ArrayList<>();

        for (Object revision : revisions) {
            Object[] revisionData = (Object[]) revision;
            Item itemRevision = (Item) revisionData[0];
            CustomRevisionEntity revisionEntity = (CustomRevisionEntity) revisionData[1];
            RevisionType revisionType = (RevisionType) revisionData[2];

            String revType = switch (revisionType) {
                case ADD -> "ADD";
                case MOD -> "MOD";
                case DEL -> "DEL";
            };

            LocalDateTime timestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(revisionEntity.getTimestamp()),
                    ZoneId.systemDefault()
            );

            history.add(new ItemHistoryResponse(
                    revisionEntity.getId(),
                    revType,
                    timestamp,
                    revisionEntity.getChangedBy(),
                    itemRevision.getTitle(),
                    itemRevision.getContent()
            ));
        }

        return history;
    }

    public ShareItemResponse shareItem(UUID itemId, ShareItemRequest request, String userLogin) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));

        User owner = userRepository.findByLogin(userLogin)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!item.getOwner().getId().equals(owner.getId())) {
            throw new ForbiddenAccessException("Only the owner can manage access to this item");
        }

        if (owner.getLogin().equals(userLogin)) {
            throw new ForbiddenAccessException("Owner cannot share access with themselves");
        }

        User targetUser = userRepository.findById(request.userId())
                .orElseThrow(() -> new UsernameNotFoundException("Target user not found"));

        PermissionRole permissionRole;
        try {
            permissionRole = PermissionRole.valueOf(request.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role. Must be VIEWER or EDITOR");
        }

        ItemPermission permission = itemPermissionRepository.findByItemIdAndUserId(itemId, request.userId())
                .orElse(null);

        boolean isUpdate = permission != null;

        if (permission == null) {
            permission = new ItemPermission();
            permission.setItem(item);
            permission.setUser(targetUser);
        }

        permission.setRole(permissionRole);
        ItemPermission savedPermission = itemPermissionRepository.save(permission);

        return new ShareItemResponse(
                itemId,
                targetUser.getId(),
                savedPermission.getRole().name(),
                LocalDateTime.now()
        );
    }

    public void revokeAccess(UUID itemId, UUID userId, String userLogin) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));

        User owner = userRepository.findByLogin(userLogin)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!item.getOwner().getId().equals(owner.getId())) {
            throw new ForbiddenAccessException("Only the owner can manage access to this item");
        }

        ItemPermission permission = itemPermissionRepository.findByItemIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ItemNotFoundException("User does not have access to this item"));

        itemPermissionRepository.delete(permission);
    }

}
