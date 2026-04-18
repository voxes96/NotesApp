package org.betacom.notesapp.repository;

import org.betacom.notesapp.model.Item;
import org.betacom.notesapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {
    
    List<Item> findByOwnerAndDeletedFalse(User owner);
    
    List<Item> findByOwnerIdAndDeletedFalse(UUID ownerId);
    
    List<Item> findByDeletedFalse();
}
