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
        }

        // Создаем уведомление для родителя
        JoinRequestNotification notif = new JoinRequestNotification();
        notif.setRequest(req);
        notif.setParentVkId(req.getParent().getVkId());
        notif.setCreatedAt(LocalDateTime.now());
        notif.setIsSent(false);

        String msgText;
        if ("APPROVED".equals(status)) {
            // Получаем информацию о группе
            Group group = req.getGroup();
            String groupName = group.getName();

            // Получаем расписание группы
            String schedule = getGroupSchedule(group);

            // Получаем ФИО тренера (с инициалами)
            String trainerName = "не назначен";
            if (group.getTrainer() != null && group.getTrainer().getFullName() != null) {
                trainerName = ru.sashil.common.util.NameUtils.toInitials(group.getTrainer().getFullName());
            }

            msgText = "✅ Заявка на вступление в группу \"" + groupName + "\" одобрена! Ребенок зачислен.\n\n" +
                    "Расписание занятий:\n" +
                    schedule + "\n\n" +
                    "Тренер: " + trainerName + "\n" +
                    "Группа: " + groupName;

            // Добавляем комментарий администратора, если он есть
            if (comment != null && !comment.trim().isEmpty()) {
                msgText += "\n\nКомментарий администратора: " + comment;
            }

            msgText += "\n\nПожалуйста, запомните эти данные.";
        } else {
            msgText = "❌ Заявка на вступление в группу \"" + req.getGroup().getName() + "\" отклонена.";
            if (comment != null && !comment.isEmpty()) {
                msgText += "\nПричина: " + comment;
            }
        }
        notif.setMessageText(msgText);
        notifRepo.save(notif);
    }

    /**
     * Форматирует расписание группы в читаемый вид
     */
    private String getGroupSchedule(Group group) {
        StringBuilder sb = new StringBuilder();

        // Маппинг дней недели
        String[] dayNames = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        LocalTime[] starts = {group.getDay1Start(), group.getDay2Start(), group.getDay3Start(),
                group.getDay4Start(), group.getDay5Start(), group.getDay6Start(),
                group.getDay7Start()};
        LocalTime[] ends = {group.getDay1End(), group.getDay2End(), group.getDay3End(),
                group.getDay4End(), group.getDay5End(), group.getDay6End(),
                group.getDay7End()};

        boolean hasSchedule = false;
        for (int i = 0; i < 7; i++) {
            if (starts[i] != null && ends[i] != null) {
                if (hasSchedule) sb.append("\n");
                sb.append(dayNames[i] + " " + starts[i] + " - " + ends[i]);
                hasSchedule = true;
            }
        }

        if (!hasSchedule) {
            sb.append("Расписание не указано");
        }

        return sb.toString();
    }


}