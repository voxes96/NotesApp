package org.betacom.notesapp.service;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.betacom.notesapp.dto.item.CreateItemRequest;
import org.betacom.notesapp.dto.item.ItemHistoryResponse;
import org.betacom.notesapp.dto.item.ItemListResponse;
import org.betacom.notesapp.dto.item.ItemResponse;
import org.betacom.notesapp.dto.item.ShareItemRequest;
import org.betacom.notesapp.dto.item.ShareItemResponse;
import org.betacom.notesapp.dto.item.UpdateItemRequest;
import org.betacom.notesapp.dto.item.UpdateItemResponse;
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
    private final EntityManager entityManager;

    ItemService(ItemRepository itemRepository, UserRepository userRepository,
                ItemPermissionRepository itemPermissionRepository, EntityManager entityManager) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.itemPermissionRepository = itemPermissionRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public ItemResponse createItem(CreateItemRequest request, String userLogin) {
        User user = findUserByLogin(userLogin);

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

    @Transactional
    public UpdateItemResponse updateItem(UUID id, UpdateItemRequest request, String userLogin) {
        Item item = findItemById(id);

        validateItemNotDeleted(item);
        validateUserCanEditItem(item, userLogin);
        validateVersionMatch(item, request.version());

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

    @Transactional
    public void deleteItem(UUID id, String userLogin) {
        Item item = findItemById(id);
        validateUserIsOwner(item, userLogin);

        item.setDeleted(true);
        itemRepository.save(item);
    }

    public List<ItemHistoryResponse> getItemHistory(UUID itemId, String userLogin) {
        Item item = findItemById(itemId);
        User user = findUserByLogin(userLogin);
        validateUserCanAccessItem(item, user);

        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        List<?> revisions = auditReader.createQuery()
                .forRevisionsOfEntity(Item.class, false, true)
                .add(AuditEntity.id().eq(itemId))
                .getResultList();

        return buildItemHistory(revisions);
    }

    @Transactional
    public ShareItemResponse shareItem(UUID itemId, ShareItemRequest request, String ownerUsername) {
        Item item = findItemById(itemId);
        User owner = findUserByLogin(ownerUsername);
        User targetUser = findUserById(request.userId());

        validateUserIsOwner(item, ownerUsername);
        validateNotSharingWithSelf(owner, targetUser);

        PermissionRole permissionRole = parsePermissionRole(request.role());

        ItemPermission permission = itemPermissionRepository
                .findByItemIdAndUserId(itemId, request.userId())
                .orElseGet(() -> createNewPermission(item, targetUser));

        permission.setRole(permissionRole);
        ItemPermission savedPermission = itemPermissionRepository.save(permission);

        return new ShareItemResponse(
                itemId,
                targetUser.getId(),
                savedPermission.getRole().name(),
                LocalDateTime.now()
        );
    }

    @Transactional
    public void revokeAccess(UUID itemId, UUID userId, String userLogin) {
        Item item = findItemById(itemId);
        validateUserIsOwner(item, userLogin);

        ItemPermission permission = itemPermissionRepository.findByItemIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ItemNotFoundException("User does not have access to this item"));

        itemPermissionRepository.delete(permission);
    }


    private User findUserByLogin(String login) {
        return userRepository.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private Item findItemById(UUID itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found or has been deleted"));
    }

    private void validateItemNotDeleted(Item item) {
        if (item.getDeleted()) {
            throw new ItemNotFoundException("Item not found or has been deleted");
        }
    }

    private void validateUserCanEditItem(Item item, String userLogin) {
        boolean isOwner = item.getOwner().getLogin().equals(userLogin);
        boolean hasEditorPermission = itemPermissionRepository
                .existsByItemAndUserLoginAndRole(item, userLogin, PermissionRole.EDITOR);

        if (!isOwner && !hasEditorPermission) {
            throw new ForbiddenAccessException("You do not have permission to edit this item");
        }
    }

    private void validateUserIsOwner(Item item, String userLogin) {
        if (!item.getOwner().getLogin().equals(userLogin)) {
            throw new ForbiddenAccessException("Only the owner can manage access to this item");
        }
    }

    private void validateVersionMatch(Item item, Integer requestVersion) {
        if (!item.getVersion().equals(requestVersion)) {
            throw new ItemVersionConflictException("Version conflict - the item has been modified by someone else", item.getVersion());
        }
    }

    private void validateUserCanAccessItem(Item item, User user) {
        boolean isOwner = item.getOwner().getId().equals(user.getId());
        boolean hasPermission = itemPermissionRepository.existsByItemIdAndUserId(item.getId(), user.getId());

        if (!isOwner && !hasPermission) {
            throw new ForbiddenAccessException("You do not have access to this item's history");
        }
    }

    private void validateNotSharingWithSelf(User owner, User targetUser) {
        if (owner.getLogin().equals(targetUser.getLogin())) {
            throw new ForbiddenAccessException("Owner cannot share access with themselves");
        }
    }

    private PermissionRole parsePermissionRole(String role) {
        try {
            return PermissionRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role. Must be VIEWER or EDITOR");
        }
    }

    private ItemPermission createNewPermission(Item item, User user) {
        ItemPermission permission = new ItemPermission();
        permission.setItem(item);
        permission.setUser(user);
        return permission;
    }

    private List<ItemHistoryResponse> buildItemHistory(List<?> revisions) {
        List<ItemHistoryResponse> history = new ArrayList<>();

        for (Object revision : revisions) {
            Object[] revisionData = (Object[]) revision;
            Item itemRevision = (Item) revisionData[0];
            CustomRevisionEntity revisionEntity = (CustomRevisionEntity) revisionData[1];
            RevisionType revisionType = (RevisionType) revisionData[2];

            String revType = mapRevisionType(revisionType);
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

    private String mapRevisionType(RevisionType revisionType) {
        return switch (revisionType) {
            case ADD -> "ADD";
            case MOD -> "MOD";
            case DEL -> "DEL";
        };
    }

}
