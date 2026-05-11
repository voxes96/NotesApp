package org.betacom.notesapp.service;

import org.betacom.notesapp.dto.*;
import org.betacom.notesapp.exception.ForbiddenAccessException;
import org.betacom.notesapp.exception.ItemNotFoundException;
import org.betacom.notesapp.exception.ItemVersionConflictException;
import org.betacom.notesapp.model.*;
import org.betacom.notesapp.repository.ItemPermissionRepository;
import org.betacom.notesapp.repository.ItemRepository;
import org.betacom.notesapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    public static final String OWNER_USERNAME = "owner";
    public static final String EDITOR_USERNAME = "editor";
    public static final String VIEWER_USERNAME = "viewer";
    public static final String OTHERUSER_USERNAME = "otheruser";


    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ItemPermissionRepository itemPermissionRepository;

    @InjectMocks
    private ItemService itemService;

    private User owner;
    private User editor;
    private User viewer;
    private User otherUser;
    private Item item;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setLogin(OWNER_USERNAME);

        editor = new User();
        editor.setId(UUID.randomUUID());
        editor.setLogin(EDITOR_USERNAME);

        viewer = new User();
        viewer.setId(UUID.randomUUID());
        viewer.setLogin(VIEWER_USERNAME);

        otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setLogin(OTHERUSER_USERNAME);

        item = new Item();
        item.setId(UUID.randomUUID());
        item.setTitle("Test Item");
        item.setContent("Test Content");
        item.setVersion(1);
        item.setOwner(owner);
        item.setDeleted(false);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
    }

    // ===== CREATE ITEM TESTS =====

    @Test
    void createItem_Success() {
        CreateItemRequest request = new CreateItemRequest(
                "New Title",
                "New Content"
        );

        when(userRepository.findByLogin(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item savedItem = invocation.getArgument(0);
            savedItem.setId(UUID.randomUUID());
            savedItem.setVersion(0);
            savedItem.setCreatedAt(LocalDateTime.now());
            savedItem.setUpdatedAt(LocalDateTime.now());
            return savedItem;
        });

        ItemResponse response = itemService.createItem(request, OWNER_USERNAME);

        assertNotNull(response);
        assertEquals("New Title", response.title());
        assertEquals("New Content", response.content());
        assertEquals(0, response.version());
        assertEquals(owner.getId(), response.ownerId());

        verify(userRepository).findByLogin(OWNER_USERNAME);
        verify(itemRepository).save(any(Item.class));
    }

    @Test
    void createItem_UserNotFound_ThrowsException() {
        CreateItemRequest request = new CreateItemRequest(
                "New Title",
                "New Content"
        );

        when(userRepository.findByLogin("nonexistent")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> itemService.createItem(request, "nonexistent"));

        verify(userRepository).findByLogin("nonexistent");
        verify(itemRepository, never()).save(any());
    }

    // ===== UPDATE ITEM - AUTHORIZATION TESTS =====

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void updateItem_OwnerCanEdit_Success() {
        UUID itemId = item.getId();
        UpdateItemRequest request = new UpdateItemRequest(
                "Updated Title",
                "Updated Content",
                1
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemPermissionRepository.existsByItemAndUserLoginAndRole(item, OWNER_USERNAME, PermissionRole.EDITOR)).thenReturn(false);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item savedItem = invocation.getArgument(0);
            savedItem.setVersion(2);
            savedItem.setUpdatedAt(LocalDateTime.now());
            return savedItem;
        });

        UpdateItemResponse response = itemService.updateItem(itemId, request, OWNER_USERNAME);

        assertNotNull(response);
        assertEquals("Updated Title", response.title());
        assertEquals("Updated Content", response.content());
        assertEquals(2, response.version());

        verify(itemRepository).findById(itemId);
        verify(itemRepository).save(any(Item.class));
    }

    @Test
    void updateItem_EditorCanEdit_Success() {
        UUID itemId = item.getId();
        UpdateItemRequest request = new UpdateItemRequest(
                "Updated by Editor",
                "Content by Editor",
                1
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemPermissionRepository.existsByItemAndUserLoginAndRole(item, EDITOR_USERNAME, PermissionRole.EDITOR))
                .thenReturn(true);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item savedItem = invocation.getArgument(0);
            savedItem.setVersion(2);
            savedItem.setUpdatedAt(LocalDateTime.now());
            return savedItem;
        });

        UpdateItemResponse response = itemService.updateItem(itemId, request, EDITOR_USERNAME);

        assertNotNull(response);
        assertEquals("Updated by Editor", response.title());

        verify(itemRepository).findById(itemId);
        verify(itemPermissionRepository).existsByItemAndUserLoginAndRole(item, EDITOR_USERNAME, PermissionRole.EDITOR);
        verify(itemRepository).save(any(Item.class));
    }

    @Test
    void updateItem_ViewerCannotEdit_ThrowsForbiddenException() {
        UUID itemId = item.getId();
        UpdateItemRequest request = new UpdateItemRequest(
                "Attempted Update",
                "Content",
                1
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemPermissionRepository.existsByItemAndUserLoginAndRole(item, VIEWER_USERNAME, PermissionRole.EDITOR))
                .thenReturn(false);

        assertThrows(ForbiddenAccessException.class, () -> itemService.updateItem(itemId, request, VIEWER_USERNAME));

        verify(itemRepository).findById(itemId);
        verify(itemRepository, never()).save(any());
    }

    @Test
    void updateItem_UnauthorizedUser_ThrowsForbiddenException() {
        UUID itemId = item.getId();
        UpdateItemRequest request = new UpdateItemRequest(
                "Unauthorized Update",
                "Content",
                1
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemPermissionRepository.existsByItemAndUserLoginAndRole(eq(item), eq(OTHERUSER_USERNAME), any())).thenReturn(false);

        assertThrows(ForbiddenAccessException.class, () -> itemService.updateItem(itemId, request, OTHERUSER_USERNAME));

        verify(itemRepository).findById(itemId);
        verify(itemRepository, never()).save(any());
    }

    // ===== VERSION CONFLICT TESTS =====

    @Test
    void updateItem_VersionMismatch_ThrowsConflictException() {
        UUID itemId = item.getId();
        UpdateItemRequest request = new UpdateItemRequest(
                "Update with wrong version",
                "Content",
                0 // Wrong version, item has version 1
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        ItemVersionConflictException exception = assertThrows(ItemVersionConflictException.class,
                () -> itemService.updateItem(itemId, request, OWNER_USERNAME));

        assertEquals(1, exception.getCurrentVersion());
        assertTrue(exception.getMessage().contains("Version conflict"));

        verify(itemRepository).findById(itemId);
        verify(itemRepository, never()).save(any());
    }

    @Test
    void updateItem_DeletedItem_ThrowsNotFoundException() {
        UUID itemId = item.getId();
        item.setDeleted(true);

        UpdateItemRequest request = new UpdateItemRequest(
                "Update",
                "Content",
                1
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(ItemNotFoundException.class, () -> itemService.updateItem(itemId, request, OWNER_USERNAME));

        verify(itemRepository).findById(itemId);
        verify(itemRepository, never()).save(any());
    }

    // ===== SHARE ITEM TESTS =====

    @Test
    void shareItem_OwnerSharesWithEditor_Success() {
        UUID itemId = item.getId();
        ShareItemRequest request = new ShareItemRequest(
                editor.getId(),
                "EDITOR"
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findByLogin(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(userRepository.findById(editor.getId())).thenReturn(Optional.of(editor));
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, editor.getId())).thenReturn(Optional.empty());
        when(itemPermissionRepository.save(any(ItemPermission.class))).thenAnswer(
                invocation -> invocation.getArgument(0));

        ShareItemResponse response = itemService.shareItem(itemId, request, OWNER_USERNAME);

        assertNotNull(response);
        assertEquals(itemId, response.itemId());
        assertEquals(editor.getId(), response.userId());
        assertEquals("EDITOR", response.role());
        assertNotNull(response.grantedAt());

        verify(itemPermissionRepository).save(any(ItemPermission.class));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void shareItem_OwnerSharesWithOwner_ThrowsForbiddenException() {
        UUID itemId = item.getId();
        ShareItemRequest request = new ShareItemRequest(
                owner.getId(),
                "EDITOR"
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findByLogin(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, owner.getId())).thenReturn(Optional.empty());
        when(itemPermissionRepository.save(any(ItemPermission.class))).thenAnswer(
                invocation -> invocation.getArgument(0));

        assertThrows(ForbiddenAccessException.class, () -> itemService.shareItem(itemId, request, OWNER_USERNAME));

        verify(itemPermissionRepository, never()).save(any());
    }

    @Test
    void shareItem_UpdateExistingRole_Success() {
        UUID itemId = item.getId();
        ShareItemRequest request = new ShareItemRequest(
                viewer.getId(),
                "EDITOR" // Upgrade from VIEWER to EDITOR
        );

        ItemPermission existingPermission = new ItemPermission();
        existingPermission.setItem(item);
        existingPermission.setUser(viewer);
        existingPermission.setRole(PermissionRole.VIEWER);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findByLogin(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(userRepository.findById(viewer.getId())).thenReturn(Optional.of(viewer));
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, viewer.getId()))
                .thenReturn(Optional.of(existingPermission));
        when(itemPermissionRepository.save(any(ItemPermission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareItemResponse response = itemService.shareItem(itemId, request, OWNER_USERNAME);

        assertNotNull(response);
        assertEquals("EDITOR", response.role());

        verify(itemPermissionRepository).save(argThat(perm ->
                perm.getRole() == PermissionRole.EDITOR
        ));
    }

    @Test
    void shareItem_NonOwnerCannotShare_ThrowsForbiddenException() {
        UUID itemId = item.getId();
        ShareItemRequest request = new ShareItemRequest(
                otherUser.getId(),
                "VIEWER"
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findByLogin(EDITOR_USERNAME)).thenReturn(Optional.of(editor));
        when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));

        assertThrows(ForbiddenAccessException.class, () -> itemService.shareItem(itemId, request, EDITOR_USERNAME));

        verify(itemPermissionRepository, never()).save(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void shareItem_InvalidRole_ThrowsIllegalArgumentException() {
        UUID itemId = item.getId();
        ShareItemRequest request = new ShareItemRequest(
                editor.getId(),
                "INVALID_ROLE"
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findByLogin(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(userRepository.findById(editor.getId())).thenReturn(Optional.of(editor));
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, editor.getId())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> itemService.shareItem(itemId, request, OWNER_USERNAME));

        verify(itemPermissionRepository, never()).save(any());
    }

    @Test
    void shareItem_TargetUserNotFound_ThrowsUsernameNotFoundException() {
        UUID itemId = item.getId();
        UUID nonExistentUserId = UUID.randomUUID();
        ShareItemRequest request = new ShareItemRequest(
                nonExistentUserId,
                "VIEWER"
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findByLogin(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> itemService.shareItem(itemId, request, OWNER_USERNAME));

        verify(itemPermissionRepository, never()).save(any());
    }

    // ===== REVOKE ACCESS TESTS =====

    @Test
    void revokeAccess_OwnerRevokesAccess_Success() {
        UUID itemId = item.getId();
        ItemPermission permission = new ItemPermission();
        permission.setItem(item);
        permission.setUser(viewer);
        permission.setRole(PermissionRole.VIEWER);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findByLogin(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, viewer.getId()))
                .thenReturn(Optional.of(permission));

        itemService.revokeAccess(itemId, viewer.getId(), OWNER_USERNAME);

        verify(itemPermissionRepository).delete(permission);
    }

    @Test
    void revokeAccess_NonOwnerCannotRevoke_ThrowsForbiddenException() {
        UUID itemId = item.getId();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findByLogin(EDITOR_USERNAME)).thenReturn(Optional.of(editor));

        assertThrows(ForbiddenAccessException.class, () -> itemService.revokeAccess(itemId, viewer.getId(), EDITOR_USERNAME));

        verify(itemPermissionRepository, never()).delete(any());
    }

    @Test
    void revokeAccess_PermissionNotFound_ThrowsNotFoundException() {
        UUID itemId = item.getId();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findByLogin(OWNER_USERNAME)).thenReturn(Optional.of(owner));
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, editor.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> itemService.revokeAccess(itemId, editor.getId(), OWNER_USERNAME));

        verify(itemPermissionRepository, never()).delete(any());
    }

    // ===== DELETE ITEM TESTS =====

    @Test
    void deleteItem_OwnerCanDelete_Success() {
        UUID itemId = item.getId();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.deleteItem(itemId, OWNER_USERNAME);

        assertTrue(item.getDeleted());
        verify(itemRepository).save(item);
    }

    @Test
    void deleteItem_NonOwnerCannotDelete_ThrowsForbiddenException() {
        UUID itemId = item.getId();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(ForbiddenAccessException.class, () -> itemService.deleteItem(itemId, EDITOR_USERNAME));

        assertFalse(item.getDeleted());
        verify(itemRepository, never()).save(any());
    }

}
