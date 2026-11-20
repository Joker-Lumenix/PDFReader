package com.peng.pdfreader.notification.trigger

import com.peng.pdfreader.R

/**
 * The content of a notification.
 *
 * The payload of firebase carries its own text; without one the app uses the
 * campaign of the design - "Photos to PDF" for the small notification and the
 * merge of two documents for the big one.
 */
data class NotificationPayload(
    val title: String,
    val text: String,
    val target: NotificationTarget = NotificationTarget.IMAGE_TO_PDF,
    val style: NotificationStyle = NotificationStyle.NORMAL,
)

/** The page the notification leads to. */
enum class NotificationTarget(
    /** The icon of the small notification. */
    val iconRes: Int,
    /** The picture of the big notification. */
    val imageRes: Int,
    /**
     * The id of the notification of the function.
     *
     * Every function has its own id, so the notifications of the sequence stay
     * side by side in the shade of the system.
     */
    val notifyId: Int,
) {
    /** The home screen of the app. */
    HOME(R.mipmap.ic_launcher, R.mipmap.bg_notification_merge_pdf, 4401),

    /** The flow that turns pictures into a PDF. */
    IMAGE_TO_PDF(R.mipmap.nf_image_to_pdf, R.mipmap.bg_notification_image_to_pdf, 4402),

    /** The flow that merges documents. */
    MERGE_PDF(R.mipmap.nf_merge_pdf, R.mipmap.bg_notification_merge_pdf, 4403),

    /** The flow that splits a document. */
    SPLIT_PDF(R.mipmap.nf_split_pdf, R.mipmap.bg_notification_split_pdf, 4404),

    /** The flow that encrypts a document. */
    LOCK(R.mipmap.nf_lock_pdf, R.mipmap.bg_notification_lock_pdf, 4405),

    /** The flow that removes the password of a document. */
    UNLOCK(R.mipmap.nf_unlock_pdf, R.mipmap.bg_notification_unlock_pdf, 4406),

    /** The flow that compresses a document. */
    COMPRESS(R.mipmap.nf_compress, R.mipmap.bg_notification_compress_pdf, 4407),

    /** The flow that prints a document. */
    PRINT(R.mipmap.nf_print_pdf, R.mipmap.bg_notification_print_pdf, 4408),

    /** The flow that scans a document. */
    SCAN(R.mipmap.nf_scan_pdf, R.mipmap.bg_notification_scan_pdf, 4409),

    /** The list of the files. */
    FILES(R.mipmap.ic_launcher, R.mipmap.bg_notification_merge_pdf, 4410),

    ;

    companion object {
        /** The target of the given key, null when the key is unknown. */
        fun of(key: String?): NotificationTarget? = entries.firstOrNull { it.name == key }
    }
}

/** The design the notification is built with. */
enum class NotificationStyle {
    /** The small notification with an icon and a button. */
    NORMAL,

    /** The big notification with the picture of the design. */
    BIG,

    ;

    companion object {
        /** The style of the given key, null when the key is unknown. */
        fun of(key: String?): NotificationStyle? = entries.firstOrNull { it.name == key }
    }
}
