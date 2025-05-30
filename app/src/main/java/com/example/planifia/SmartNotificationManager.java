package com.example.planifia;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SmartNotificationManager {

    private static final String CHANNEL_ID = "smart_reminder_channel";
    private static final String CHANNEL_NAME = "Smart Reminders";
    private static final String CHANNEL_DESCRIPTION = "AI-powered task reminders";
    private static final String TAG = "SmartNotificationMgr";

    private Context context;
    private TaskAnalyzer taskAnalyzer;

    public SmartNotificationManager(Context context) {
        this.context = context;
        createNotificationChannel();

        // Initialiser TaskAnalyzer de manière sécurisée
        this.taskAnalyzer = new TaskAnalyzer();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(CHANNEL_DESCRIPTION);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void scheduleSmartReminders() {
        // Vérifier si l'utilisateur est connecté avant d'analyser les tâches
        if (!taskAnalyzer.isUserLoggedIn()) {
            Log.d(TAG, "Impossible de programmer des rappels: utilisateur non connecté");
            return;
        }

        taskAnalyzer.analyzeTasks(new TaskAnalyzer.TaskAnalysisCallback() {
            @Override
            public void onAnalysisComplete(List<Task_Class> prioritizedTasks) {
                if (!prioritizedTasks.isEmpty()) {
                    // Prendre les 3 tâches les plus prioritaires
                    int tasksToRemind = Math.min(3, prioritizedTasks.size());
                    for (int i = 0; i < tasksToRemind; i++) {
                        Task_Class task = prioritizedTasks.get(i);
                        scheduleReminderForTask(task, i);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Erreur lors de l'analyse des tâches: " + errorMessage);
                // Vous pourriez afficher un message à l'utilisateur ici si nécessaire
            }
        });
    }

    private void scheduleReminderForTask(Task_Class task, int taskIndex) {
        // Calculer un moment optimal pour rappeler cette tâche
        Calendar calendar = Calendar.getInstance();

        // Vérifier si la tâche a une échéance proche (moins de 24h)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date dueDate = sdf.parse(task.getDueDate());
            Date today = new Date();
            long diffInMillies = dueDate.getTime() - today.getTime();
            long diffInHours = diffInMillies / (60 * 60 * 1000);

            if (diffInHours <= 24) {
                // Échéance proche: notification immédiate
                calendar.add(Calendar.MINUTE, 5);
                Log.d(TAG, "Tâche avec échéance proche: " + task.getTitle() + " - notification dans 5 minutes");
            } else {
                // Échéance plus lointaine: délai aléatoire
                Random random = new Random();
                int hoursToAdd = random.nextInt(24) + 1;
                calendar.add(Calendar.HOUR_OF_DAY, hoursToAdd);
                Log.d(TAG, "Tâche standard: " + task.getTitle() + " - notification dans " + hoursToAdd + " heures");
            }
        } catch (ParseException e) {
            // En cas d'erreur, utiliser le comportement par défaut
            Log.e(TAG, "Erreur lors de l'analyse de la date: " + e.getMessage());
            Random random = new Random();
            int hoursToAdd = random.nextInt(24) + 1;
            calendar.add(Calendar.HOUR_OF_DAY, hoursToAdd);
        }

        // Créer l'intent pour la notification
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra("taskTitle", task.getTitle());
        intent.putExtra("taskDescription", task.getDescription());
        intent.putExtra("notificationId", taskIndex);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                taskIndex,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Programmer l'alarme
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
        );
    }

    public void showTaskReminder(String taskTitle, String taskDescription, int notificationId) {
        Intent intent = new Intent(context, PrioritizedTasksActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ai)
                .setContentTitle("Prioritized Task: " + taskTitle)
                .setContentText(taskDescription)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, builder.build());
    }
}