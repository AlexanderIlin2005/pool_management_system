package ru.sashil.admin.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.sashil.admin.model.*;
import ru.sashil.admin.repository.GroupRepository;
import ru.sashil.admin.service.AttendanceService;
import ru.sashil.admin.service.LessonService;
import ru.sashil.admin.service.WsNotificationService;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/attendance")
public class AttendanceMarkController {

    @Autowired private AttendanceService attendanceService;
    @Autowired private LessonService lessonService;
    @Autowired private WsNotificationService wsNotificationService;

    @GetMapping("/mark/{lessonId}")
    public String markPage(@PathVariable Long lessonId, Model model, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        Optional<PoolLesson> lessonOpt = lessonService.getLessonForAttendance(lessonId, user);
        if (lessonOpt.isEmpty()) return "redirect:/schedule?error=access_denied";

        PoolLesson lesson = lessonOpt.get();

        List<ChildSimple> children = attendanceService.getEligibleChildren(lesson.getGroup().getId(), lesson.getLessonDate());

        List<Attendance> existing = attendanceService.getByLessonId(lessonId);

        Map<Long, Attendance.Status> currentMarks = new HashMap<>();
        Map<Long, String> currentComments = new HashMap<>();

        for (Attendance att : existing) {
            currentMarks.put(att.getChild().getId(), att.getStatus());
            if (att.getComment() != null) {
                currentComments.put(att.getChild().getId(), att.getComment());
            }
        }

        // Считаем сколько детей отмечено
        int totalChildren = children.size();
        int markedChildren = existing.size();

        model.addAttribute("lesson", lesson);
        model.addAttribute("children", children);
        model.addAttribute("currentMarks", currentMarks);
        model.addAttribute("currentComments", currentComments);
        model.addAttribute("totalChildren", totalChildren);
        model.addAttribute("markedChildren", markedChildren);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "schedule");
        return "attendance-mark";
    }

    @PostMapping("/save/{lessonId}")
    public String saveMarks(@PathVariable Long lessonId,
                            @RequestParam Map<String, String> allParams,
                            @RequestParam(required = false) Boolean skipUnmarked,
                            HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        Optional<PoolLesson> lessonOpt = lessonService.getLessonForAttendance(lessonId, user);
        if (lessonOpt.isEmpty()) return "redirect:/schedule?error=access_denied";

        Map<Long, String> marks = new HashMap<>();
        Map<Long, String> comments = new HashMap<>();

        // Получаем всех детей группы для определения, кто не отмечен
        PoolLesson lesson = lessonOpt.get();
        List<ChildSimple> allChildren = attendanceService.getEligibleChildren(lesson.getGroup().getId(), lesson.getLessonDate());
        Set<Long> markedChildIds = new HashSet<>();

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.startsWith("marks[")) {
                try {
                    String idStr = key.substring(6, key.length() - 1);
                    Long childId = Long.parseLong(idStr);
                    // Добавляем только если статус не пустой
                    if (value != null && !value.isEmpty()) {
                        marks.put(childId, value);
                        markedChildIds.add(childId);
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки парсинга
                }
            } else if (key.startsWith("comments[")) {
                try {
                    String idStr = key.substring(9, key.length() - 1);
                    Long childId = Long.parseLong(idStr);
                    comments.put(childId, value);
                } catch (Exception e) {
                    // Игнорируем ошибки парсинга
                }
            }
        }

        // Если включена опция "отметить всех остальных как ABSENT"
        if (skipUnmarked != null && skipUnmarked) {
            for (ChildSimple child : allChildren) {
                if (!markedChildIds.contains(child.getId())) {
                    marks.put(child.getId(), "ABSENT");
                }
            }
        }

        // Проверяем, есть ли что сохранять
        if (marks.isEmpty()) {
            return "redirect:/attendance/mark/" + lessonId + "?error=no_marks";
        }

        attendanceService.saveAttendanceWithComments(lessonId, marks, comments, user);
        wsNotificationService.sendUpdateNotification("ATTENDANCE_MARK_UPDATED");

        // Если отмечены не все дети и не включена опция автоматической отметки
        int totalChildren = allChildren.size();
        int markedCount = marks.size();
        if (markedCount < totalChildren && (skipUnmarked == null || !skipUnmarked)) {
            return "redirect:/attendance/mark/" + lessonId + "?success=true&partial=true";
        }

        return "redirect:/attendance/mark/" + lessonId + "?success=true";
    }
}