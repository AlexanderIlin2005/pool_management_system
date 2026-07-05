package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.sashil.admin.model.Group;
import ru.sashil.admin.model.Pool;
import ru.sashil.admin.repository.GroupRepository;
import ru.sashil.admin.repository.PoolRepository;

import java.util.List;
import java.util.Optional;

@Service
public class GroupService {
    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PoolRepository poolRepository;

    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    public List<Pool> getAllPools() {
        return poolRepository.findAll();
    }

    public void saveGroup(Group group) {
        if (group.getId() == null) { // Если это новая группа
            if (groupRepository.existsByNumber(group.getNumber())) {
                throw new IllegalArgumentException("Группа с таким номером уже существует!");
            }
        } else { // Если редактируем существующую
            Optional<Group> existing = groupRepository.findById(group.getId());
            if (existing.isPresent() && !existing.get().getNumber().equals(group.getNumber())) {
                if (groupRepository.existsByNumber(group.getNumber())) {
                    throw new IllegalArgumentException("Группа с таким номером уже существует!");
                }
            }
        }
        groupRepository.save(group);
    }

    public Optional<Group> getGroupById(Long id) {
        return groupRepository.findById(id);
    }

    public void deleteGroup(Long id) {
        groupRepository.deleteById(id);
    }
}