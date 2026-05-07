package org.betacom.notesapp.service;

import org.betacom.notesapp.dto.CreateItemRequest;
import org.betacom.notesapp.dto.ItemResponse;
import org.betacom.notesapp.model.Item;
import org.betacom.notesapp.model.User;
import org.betacom.notesapp.repository.ItemRepository;
import org.betacom.notesapp.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    ItemService(ItemRepository itemRepository, UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
    }

    public ItemResponse createItem(CreateItemRequest request, String userLogin) {
        User user = userRepository.findByLogin(userLogin)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        Item item = new Item();
        item.setTitle(request.getTitle());
        item.setContent(request.getContent());
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

}
