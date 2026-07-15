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

        // Теперь здесь список ChildSimple, в котором нет поля skill
        List<ChildSimple> children = attendanceService.getEligibleChildren(lesson.getGroup().getId(), lesson.getLessonDate());

        List<Attendance> existing = attendanceService.getByLessonId(lessonId);
        Map<Long, Attendance.Status> currentMarks = existing.stream()
                .collect(Collectors.toMap(a -> a.getChild().getId(), Attendance::getStatus));

        model.addAttribute("lesson", lesson);
        model.addAttribute("children", children); // Передаем упрощенный список
        model.addAttribute("currentMarks", currentMarks);
        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("role", user.getRole());
        model.addAttribute("activePage", "schedule");
        return "attendance-mark";
    }

    @PostMapping("/save/{lessonId}")
    public String saveMarks(@PathVariable Long lessonId,
                            @RequestParam Map<String, String> allParams,
                            HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("currentUser");
        if (user == null) return "redirect:/login";

        Optional<PoolLesson> lessonOpt = lessonService.getLessonForAttendance(lessonId, user);
        if (lessonOpt.isEmpty()) return "redirect:/schedule?error=access_denied";

        // 1. Разбираем статусы (ключи вида "marks[ID]")
        Map<Long, String> marks = new HashMap<>();
        // 2. Разбираем комментарии (ключи вида "comments[ID]")
        Map<Long, String> comments = new HashMap<>();

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.startsWith("marks[")) {
                // Извлекаем ID: убираем "marks[" и "]"
                try {
                    String idStr = key.substring(6, key.length() - 1);
                    Long childId = Long.parseLong(idStr);
                    marks.put(childId, value);
                } catch (Exception e) {
                    // Игнорируем некорректные ключи
                }
            } else if (key.startsWith("comments[")) {
                try {
                    String idStr = key.substring(9, key.length() - 1);
                    Long childId = Long.parseLong(idStr);
                    if (value != null && !value.isEmpty()) {
                        comments.put(childId, value);
                    }
                } catch (Exception e) {
                    // Игнорируем
                }
            }
        }

        attendanceService.saveAttendanceWithComments(lessonId, marks, comments, user);
        wsNotificationService.sendUpdateNotification("ATTENDANCE_MARK_UPDATED");
        return "redirect:/attendance/mark/" + lessonId + "?success=true";
    }

}