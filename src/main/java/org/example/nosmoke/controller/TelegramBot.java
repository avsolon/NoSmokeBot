package org.example.nosmoke.controller;

import lombok.RequiredArgsConstructor;
import org.example.nosmoke.model.UserState;
import org.example.nosmoke.service.SmokingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {

    private final SmokingService smokingService;

    @Value("${bot.name}")
    private String botName;

    @Value("${bot.token}")
    private String botToken;

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            var message = update.getMessage();
            if (message.hasText()) {
                String input = message.getText();
                UserState state = smokingService.findByUserId(message.getFrom().getId());

                if (state == null) {
                    // Начало диалога: ждём текущее количество сигарет
                    if (input.equals("/start")) {
                        handleStartCommand(message);
                    } else if (input.matches("\\d+")) {
                        handleCurrentCigsInput(input, message); // Сразу обрабатываем ввод числа
                    } else {
                        sendMessage("Введена неизвестная команда. Воспользуйтесь командой '/start'.", message.getChatId());
                    }
                } else if (state.isAwaitingTarget()) {
                    // Если мы уже знаем текущее количество и ждём целевое
                    if (input.matches("\\d+")) {
                        handleTargetInput(input, message);
                    } else {
                        sendMessage("Введите целочисленное значение для целевого количества сигарет.", message.getChatId());
                    }
                } else {
                    // Обычные команды после окончания диалога
                    switch (input.toLowerCase()) {
                        case "/start":
                            handleStartCommand(message);
                            break;
                        case "покурил":
                            handleSmokedEvent(message);
                            break;
                        case "когда курить":
                            handleWhenToSmoke(message);
                            break;
                        case "сколько выкурил":
                            handleCigsCount(message);
                            break;
                        default:
                            sendMessage("Неверная команда.", message.getChatId());
                    }
                }
            }
        }
    }

    private void handleCigsCount(Message message) {
        UserState state = smokingService.findByUserId(message.getFrom().getId());
        if (state != null) {
            int totalCigsToday = state.getTotalCigsToday();
            int targetCigsPerDay = state.getTargetCigsPerDay();
            sendMessage("Ты выкурил " + totalCigsToday + " из " + targetCigsPerDay + " сигарет.", message.getChatId());
        } else {
            sendMessage("Сначала укажите начальное количество сигарет.", message.getChatId());
        }
    }

    private void handleStartCommand(Message message) {
        SendMessage response = new SendMessage(
                message.getChatId().toString(), // Преобразование в строку
                "Привет! Я твой помощник по контролю курения.\n\n" +
                        "Начнём:\n" +
                        "- Укажи текущее количество сигарет в день:"
        );
        try {
            execute(response);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage());
        }
        removeKeyboard(message.getChatId());
    }

    private void handleTargetInput(String input, Message message) {
        int targetCigsPerDay = Integer.parseInt(input);
        UserState state = smokingService.findByUserId(message.getFrom().getId());
        state.setTargetCigsPerDay(targetCigsPerDay);
        state.setAwaitingTarget(false); // Больше не ждём целевое количество
        smokingService.saveUserState(state);

        sendMainKeyboard(message.getChatId());
        sendMessage("✅ Цель установлена! Используй кнопки ниже:", message.getChatId());
    }


    private void handleCurrentCigsInput(String input, Message message) {
        Long chatId = message.getChatId();
        Long userId = message.getFrom().getId();

        try {
            int cigsPerDay = Integer.parseInt(input);

            // Валидация ввода
            if (cigsPerDay < 1 || cigsPerDay > 80) {
                SendMessage errorMsg = new SendMessage(
                        chatId.toString(),
                        "⚠️ Введи адекватное число сижек от 1 до 80 (если ты куришь в день больше, то я тебе не помогу, никто не поможет):"
                );
                errorMsg.setReplyMarkup(new ReplyKeyboardRemove(true));
                execute(errorMsg);
                return;
            }

            // Создание/обновление состояния пользователя
            UserState state = smokingService.findByUserId(userId);
            if (state == null) {
                state = UserState.builder()
                        .userId(userId)
                        .currentCigsPerDay(cigsPerDay)
                        .awaitingTarget(true)
                        .totalCigsToday(0)
                        .isTimerRunning(false)
                        .moneySavedToday(0.0)
                        .build();
            } else {
                state.setCurrentCigsPerDay(cigsPerDay);
                state.setAwaitingTarget(true);
            }

            // Сохранение и ответ
            smokingService.saveUserState(state);

            SendMessage response = new SendMessage(
                    chatId.toString(),
                    "🎯 Теперь введите ЦЕЛЕВОЕ количество сигарет в день:"
            );
            response.setReplyMarkup(new ReplyKeyboardRemove(true));
            execute(response);

        } catch (NumberFormatException e) {
            sendValidationError(chatId, "❌ Это не число! Введите цифрами:");
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage());
            sendMessage("🚫 Произошла техническая ошибка", chatId);
        }
    }

    private void sendValidationError(Long chatId, String message) {
        try {
            SendMessage msg = new SendMessage(
                    chatId.toString(),
                    message
            );
            msg.setReplyMarkup(new ReplyKeyboardRemove(true));
            execute(msg);
        } catch (TelegramApiException ex) {
            log.error("Ошибка отправки валидационного сообщения: {}", ex.getMessage());
        }
    }

    private void handleSmokedEvent(Message message) {
        UserState state = smokingService.findByUserId(message.getFrom().getId());
        if (state.getTotalCigsToday() >= state.getTargetCigsPerDay()) {
            sendMessage("Вы уже достигли дневного лимита!", message.getChatId());
            return;
        }
        if (state != null) {
            state.setTotalCigsToday(state.getTotalCigsToday() + 1); // Инкрементируем количество выкуренных сигарет
            smokingService.setNextSmokeTime(state); // Устанавливаем время следующего перекура
            smokingService.saveUserState(state);

            sendMessage("Вы можете снова курить примерно через " +
                            formatTime(smokingService.calculateInterval(state)),
                    message.getChatId());
        } else {
            sendMessage("Сначала укажите начальное количество сигарет.", message.getChatId());
        }
    }

    private void handleWhenToSmoke(Message message) {
        UserState state = smokingService.findByUserId(message.getFrom().getId());
        if (state != null) {
            if(state.getTotalCigsToday() == 0){
                    sendMessage("Таймер будет запущен после первого перекура.", message.getChatId());
                } else{
                    long remainingMs = state.getNextAllowedTime() - System.currentTimeMillis();

                    if (remainingMs <= 0) {
                        sendMessage("Можно дунуть прямо сейчас!", message.getChatId());
                    } else {
                    sendMessage("Следующий перекур возможен через " + formatTime(remainingMs),
                            message.getChatId());
                }
            }
        }
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "0 минут";

        long seconds = ms / 1000;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append(" ч ");
        if (minutes > 0) sb.append(minutes).append(" мин ");
        if (seconds > 0) sb.append(seconds).append(" сек");

        return sb.toString().trim();
    }

    public void sendMessage(String text, Long chatId) {
        SendMessage msg = new SendMessage(chatId.toString(), text);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage());
        }
    }


    private void sendMainKeyboard(Long chatId) {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("покурил");
        row.add("когда курить");
        row.add("Сколько выкурил");
        rows.add(row);

        keyboard.setKeyboard(rows);

        SendMessage msg = new SendMessage(chatId.toString(), "Выберите действие:");
        msg.setReplyMarkup(keyboard);

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки клавиатуры: {}", e.getMessage());
        }
    }
    private void removeKeyboard(Long chatId) {
        ReplyKeyboardRemove keyboard = new ReplyKeyboardRemove(true);
        SendMessage msg = new SendMessage(chatId.toString(), "Введи количество:");
        msg.setReplyMarkup(keyboard);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Ошибка удаления клавиатуры: {}", e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    private void processCallbackQuery(CallbackQuery callbackQuery) {
        // Дополнительная обработка нажатий кнопок
    }
}



//    @Override
//    public String getBotUsername() {
//        return botConfig.getBotName();
//    }

//    @Override
//    public String getBotToken() {
//        return botConfig.getToken();
//    }


//    @Value("${bot.token}")
//    private String botToken;
//
//    @Value("${bot.name}")
//    private String botName;

//    @Override
//    public String getBotUsername() {
//        return botName;
//    }
//


