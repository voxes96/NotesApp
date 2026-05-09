package org.betacom.notesapp.repository;

import org.betacom.notesapp.model.Item;
import org.betacom.notesapp.model.ItemPermission;
import org.betacom.notesapp.model.PermissionRole;
import org.betacom.notesapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItemPermissionRepository extends JpaRepository<ItemPermission, UUID> {

    boolean existsByItemAndUserLoginAndRole(Item item, String username, PermissionRole role);
    
    List<ItemPermission> findByItem(Item item);
    
    List<ItemPermission> findByItemId(UUID itemId);
    
    List<ItemPermission> findByUser(User user);
    
    List<ItemPermission> findByUserId(UUID userId);

}
