package ru.sashil.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sashil.admin.model.*;
import ru.sashil.admin.repository.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GroupJoinService {

    @Autowired private GroupRepository groupRepo;
    @Autowired private GroupJoinRequestRepository requestRepo;
    @Autowired private JoinRequestNotificationRepository notifRepo;
    @Autowired private GroupMemberService memberService;

    /**
     * Возвращает репозиторий заявок для использования в контроллере
     */
    public GroupJoinRequestRepository getRequestRepository() {
        return requestRepo;
    }

    /**
     * Находит подходящие группы для ребенка с сортировкой приоритетов:
     * 1. Полное совпадение (возраст + навык)
     * 2. Только навык
     * 3. Только возраст
     */
    public List<Group> findSuitableGroups(Child child) {
        List<Group> allGroups = groupRepo.findAll();
        int childAge = child.getAge();
        String childSkill = child.getSkill() != null ? child.getSkill().getDbValue() : null;

        List<Group> fullMatch = new ArrayList<>();
        List<Group> skillMatch = new ArrayList<>();
        List<Group> ageMatch = new ArrayList<>();

        for (Group g : allGroups) {
            boolean ageOk = (g.getMinAge() == null || childAge >= g.getMinAge()) &&
                    (g.getMaxAge() == null || childAge <= g.getMaxAge());

            boolean skillOk = (g.getSkill1() == null && g.getSkill2() == null) ||
                    (childSkill != null && (childSkill.equals(g.getSkill1()) || childSkill.equals(g.getSkill2())));

            if (ageOk && skillOk) fullMatch.add(g);
            else if (skillOk) skillMatch.add(g);
            else if (ageOk) ageMatch.add(g);
        }

        List<Group> result = new ArrayList<>();
        result.addAll(fullMatch);
        result.addAll(skillMatch);
        result.addAll(ageMatch);
        return result;
    }

    @Transactional
    public void createJoinRequest(Long parentId, Long childId, Long groupId) {
        GroupJoinRequest req = new GroupJoinRequest();
        Parent p = new Parent(); p.setId(parentId); req.setParent(p);
        Child c = new Child(); c.setId(childId); req.setChild(c);
        Group g = new Group(); g.setId(groupId); req.setGroup(g);

        req.setStatus("PENDING");
        req.setCreatedAt(LocalDateTime.now());
        requestRepo.save(req);
    }

    @Transactional
    public void processRequest(Long requestId, String status, String comment, Long adminId) {
        GroupJoinRequest req = requestRepo.findById(requestId).orElseThrow();
        req.setStatus(status);
        req.setAdminComment(comment);
        req.setProcessedAt(LocalDateTime.now());
        requestRepo.save(req);

        if ("APPROVED".equals(status)) {
            memberService.addChildToGroup(req.getGroup().getId(), req.getChild().getId(), null);
            // Уведомление о вступлении теперь отправляется в memberService
            // с комментарием из заявки
        }

        // Уведомление ТОЛЬКО для отклонения
        if ("REJECTED".equals(status)) {
            JoinRequestNotification notif = new JoinRequestNotification();
            notif.setRequest(req);
            notif.setParentVkId(req.getParent().getVkId());
            notif.setCreatedAt(LocalDateTime.now());
            notif.setIsSent(false);

            String msgText = "❌ Заявка на вступление в группу \"" + req.getGroup().getName() + "\" отклонена.";
            if (comment != null && !comment.isEmpty()) {
                msgText += "\nПричина: " + comment;
            }
            notif.setMessageText(msgText);
            notifRepo.save(notif);
        }
    }
}