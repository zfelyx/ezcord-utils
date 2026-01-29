package me.geckotv.ezcordutils.startup

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class PyCharmCheckStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val productName = ApplicationNamesInfo.getInstance().productName
        if (!productName.contains("PyCharm")) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("EzCord Utils Notifications")
                .createNotification(
                    "EzCord Utils WARNING",
                    "Some features may only work correctly in PyCharm.",
                    NotificationType.ERROR
                )
                .notify(project)
        }
    }
}
