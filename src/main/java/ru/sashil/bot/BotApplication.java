package ru.sashil.bot;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.messages.Message;
import ru.sashil.bot.handlers.DatabaseService;
import ru.sashil.bot.handlers.ProfileEditHandler;
import ru.sashil.bot.handlers.RegistrationHandler;
import ru.sashil.common.service.MinIOService;
import ru.sashil.common.util.CommandUtils;
import ru.sashil.common.util.ConfigLoader;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BotApplication {
    private static final Logger LOGGER = Logger.getLogger(BotApplication.class.getName());

    private static final Set<Integer> processedMessageIds = Collections.synchronizedSet(new HashSet<>());

    private static DatabaseService dbService;
    private static RegistrationHandler regHandler;
    private static ProfileEditHandler editHandler;
    private static MinIOService minioService;

    public static void main(String[] args) {
        try {
            ConfigLoader.load();

            String dbUrl = "jdbc:postgresql://" + ConfigLoader.get("DB_HOST") + ":" + ConfigLoader.get("DB_PORT") + "/" + ConfigLoader.get("DB_NAME");
            dbService = new DatabaseService(dbUrl, ConfigLoader.get("DB_USER"), ConfigLoader.get("DB_PASSWORD"));

            regHandler = new RegistrationHandler();
            editHandler = new ProfileEditHandler();
            minioService = new MinIOService();

            String vkToken = ConfigLoader.get("VK_BOT_TOKEN");
            long groupId = 239874040L;

            TransportClient transportClient = new HttpTransportClient();
            VkApiClient vk = new VkApiClient(transportClient);
            GroupActor actor = new GroupActor(groupId, vkToken);
            Random random = new Random();

            LOGGER.info("Бот запущен!");

            Integer ts = vk.messages().getLongPollServer(actor).execute().getTs();

            while (true) {
                try {
                    var response = vk.messages().getLongPollHistory(actor).ts(ts).execute();
                    List<Message> messages = response.getMessages().getItems();

                    if (messages != null && !messages.isEmpty()) {
                        for (Message message : messages) {
                            processMessage(vk, actor, message, random);
                        }
                    }
                    ts = vk.messages().getLongPollServer(actor).execute().getTs();
                    Thread.sleep(500);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Ошибка цикла: " + e.getMessage(), e);
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Критическая ошибка: " + e.getMessage(), e);
        }
    }

    private static void processMessage(VkApiClient vk, GroupActor actor, Message message, Random random) {
        Long userId = message.getFromId();
        Integer messageId = message.getId();
        String text = message.getText();

        if (processedMessageIds.contains(messageId)) return;
        processedMessageIds.add(messageId);

        try {
            // 1. Если идет регистрация
            if (regHandler.isRegistering(userId)) {
                String result = regHandler.processStep(userId, text);
                if ("SAVE_PARENT".equals(result)) {
                    Map<String, String> data = regHandler.getData(userId);
                    dbService.saveParent(userId, data.get("firstName"), data.get("lastName"),
                            data.get("middleName"), data.get("email"));
                    sendMessage(vk, actor, userId, "Регистрация завершена. Теперь вы можете добавить ребенка.", random);
                    regHandler.clearData(userId);
                } else {
                    sendMessage(vk, actor, userId, result, random);
                }
                return;
            }

            // 2. Если идет редактирование профиля
            if (editHandler.isEditing(userId)) {
                String result = editHandler.processStep(userId, text, dbService);
                sendMessage(vk, actor, userId, result, random);
                return;
            }

            // 3. Обработка команд (нормализуем ввод)
            if (text != null) {
                String cmd = CommandUtils.normalize(text);

                if (cmd.equals("начать") || cmd.equals("start")) {
                    boolean isReg = dbService.isParentRegistered(userId);
                    if (isReg) {
                        sendMessage(vk, actor, userId, "Вы уже зарегистрированы.", random);
                    } else {
                        sendMessage(vk, actor, userId, "Добро пожаловать! Давайте зарегистрируемся. Введите вашу фамилию:", random);
                        regHandler.startRegistration(userId);
                    }
                } else if (cmd.equals("редактировать") || cmd.equals("профиль")) {
                    if (!dbService.isParentRegistered(userId)) {
                        sendMessage(vk, actor, userId, "Сначала зарегистрируйтесь командой 'Начать'.", random);
                    } else {
                        Map<String, String> currentData = dbService.getParentData(userId);
                        if (currentData != null) {
                            editHandler.startEditing(userId, currentData);
                            sendMessage(vk, actor, userId, "Режим редактирования. Текущая фамилия: " + currentData.get("lastName") + ". Введите новую фамилию:", random);
                        }
                    }
                } else if (cmd.equals("справка")) {
                    sendMessage(vk, actor, userId, "Пришлите файл справки.", random);
                } else if (cmd.equals("дети")) {
                    // Заглушка для следующего шага
                    sendMessage(vk, actor, userId, "Функция управления детьми в разработке.", random);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ошибка обработки: " + e.getMessage(), e);
            try {
                sendMessage(vk, actor, userId, "Произошла внутренняя ошибка.", random);
            } catch (Exception ex) { /* ignore */ }
        }
    }

    private static void sendMessage(VkApiClient vk, GroupActor actor, Long userId, String text, Random random) throws ApiException, ClientException {
        vk.messages().sendDeprecated(actor)
                .userId(userId)
                .message(text)
                .randomId(random.nextInt(100000))
                .execute();
    }
}