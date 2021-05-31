package de.dertyp7214.rboardthememanager.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.dertyp7214.logs.helpers.Logger
import com.dertyp7214.preferencesplus.core.dp
import com.dertyp7214.preferencesplus.core.setHeight
import com.dertyp7214.preferencesplus.core.setWidth
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import de.dertyp7214.rboardthememanager.Application
import de.dertyp7214.rboardthememanager.Config
import de.dertyp7214.rboardthememanager.Config.GBOARD_PACKAGE_NAME
import de.dertyp7214.rboardthememanager.R
import de.dertyp7214.rboardthememanager.core.decodeBitmap
import de.dertyp7214.rboardthememanager.core.getAttrColor
import de.dertyp7214.rboardthememanager.core.getBitmap
import de.dertyp7214.rboardthememanager.core.runAsCommand
import de.dertyp7214.rboardthememanager.data.ThemeDataClass
import de.dertyp7214.rboardthememanager.utils.FileUtils.getThemePacksPath
import java.io.BufferedInputStream
import java.io.File
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList

@SuppressLint("SdCardPath")
fun applyTheme(
    name: String,
    withBorders: Boolean = false,
    context: Context? = null
): Boolean {
    val inputPackageName = GBOARD_PACKAGE_NAME
    val fileName =
        "/data/data/$inputPackageName/shared_prefs/${inputPackageName}_preferences.xml"
    Logger.log(
        Logger.Companion.Type.INFO,
        "APPLY",
        "[ApplyTheme]: $name $inputPackageName $fileName"
    )
    return if (!SuFile(fileName).exists()
            .also { Logger.log(Logger.Companion.Type.DEBUG, "APPLY", "[ApplyTheme]: exists = $it") }
    ) {
        context?.apply {
            Toast.makeText(this, R.string.please_open_app, Toast.LENGTH_LONG).show()
        }
        false
    } else {
        val content = SuFileInputStream.open(SuFile(fileName)).use {
            it.bufferedReader().readText()
        }.let {
            var changed = it

            changed = if ("<string name=\"additional_keyboard_theme\">" in changed)
                changed.replace(
                    "<string name=\"additional_keyboard_theme\">.*</string>".toRegex(),
                    "<string name=\"additional_keyboard_theme\">system:$name</string>"
                )
            else
                changed.replace(
                    "<map>",
                    "<map><string name=\"additional_keyboard_theme\">system:$name</string>"
                )

            // Change enable_key_border value
            changed = if ("<boolean name=\"enable_key_border\"" in changed) {
                changed.replace(
                    "<boolean name=\"enable_key_border\" value=\".*\" />".toRegex(),
                    "<boolean name=\"enable_key_border\" value=\"$withBorders\" />"
                )
            } else {
                changed.replace(
                    "<map>",
                    "<map><boolean name=\"enable_key_border\" value=\"$withBorders\" />"
                )
            }
            return@let changed
        }
        SuFileOutputStream.open(File(fileName)).writer(Charset.defaultCharset())
            .use { outputStreamWriter ->
                outputStreamWriter.write(content)
            }

        "am force-stop $inputPackageName".runAsCommand()
    }
}

@SuppressLint("SdCardPath")
fun getActiveTheme(): String {
    val inputPackageName = "com.google.android.inputmethod.latin"
    val fileLol =
        SuFile("/data/data/$inputPackageName/shared_prefs/${inputPackageName}_preferences.xml")
    return try {
        if (!fileLol.exists()) ""
        else SuFileInputStream.open(fileLol).bufferedReader().readText()
            .split("<string name=\"additional_keyboard_theme\">")
            .let { if (it.size > 1) it[1].split("</string>")[0] else "" }.replace("system:", "")
            .replace(".zip", "")
    } catch (error: Exception) {
        Logger.log(Logger.Companion.Type.ERROR, "ActiveTheme", error.message)
        ""
    }
}

fun getSoundsDirectory(): SuFile? {
    val productMedia = SuFile("/system/product/media/audio/ui/KeypressStandard.ogg")
    val systemMedia = SuFile("/system/media/audio/ui/KeypressStandard.ogg")
    return if (productMedia.exists() && productMedia.isFile) {
        SuFile("/system/product/media")
    } else if (systemMedia.exists() && systemMedia.isFile) {
        SuFile("/system/media")
    } else {
        null
    }
}

private fun writeSuFile(file: SuFile, content: String) {
    SuFileOutputStream.open(file).writer(Charset.defaultCharset())
        .use { outputStreamWriter ->
            outputStreamWriter.write(content)
        }
}

object ThemeUtils {
    fun loadThemes(): List<ThemeDataClass> {
        val themeDir =
            SuFile(Config.MAGISK_THEME_LOC)
        return themeDir.listFiles()?.filter {
            it.name.lowercase(Locale.ROOT).endsWith(".zip")
        }?.map {
            val imageFile = SuFile(Config.MAGISK_THEME_LOC, it.name.removeSuffix(".zip"))
            if (imageFile.exists()) ThemeDataClass(
                imageFile.decodeBitmap(),
                it.name.removeSuffix(".zip"),
                it.absolutePath
            )
            else ThemeDataClass(null, it.name.removeSuffix(".zip"), it.absolutePath)
        }.apply { if (this != null) Config.themeCount = size } ?: ArrayList()
    }

    fun loadPreviewThemes(context: Context): List<ThemeDataClass> {
        return loadThemesWithPath(File(getThemePacksPath(context), "previews"))
    }

    fun loadThemesWithPath(themeDir: File): List<ThemeDataClass> {
        return themeDir.listFiles()?.filter {
            it.name.lowercase(Locale.ROOT).endsWith("zip")
        }?.map {
            val imageFile = File(themeDir.absolutePath, it.name.removeSuffix(".zip"))
            if (imageFile.exists()) ThemeDataClass(
                try {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(imageFile))
                } catch (e: Exception) {
                    BitmapFactory.decodeFile(imageFile.path)
                },
                it.name.removeSuffix(".zip"),
                it.absolutePath
            )
            else ThemeDataClass(null, it.name.removeSuffix(".zip"), it.absolutePath)
        }.apply { if (this != null) Config.themeCount = size } ?: ArrayList()
    }

    fun getThemesPathFromProps(): String? {
        var path: String? = null
        "getprop ro.com.google.ime.themes_dir".runAsCommand {
            if (it.first().isNotEmpty()) path = it.first()
            Logger.log(Logger.Companion.Type.DEBUG, "Gboard-Themes-Path", path)
        }
        return path
    }

    fun checkForExistingThemes(): Boolean {
        return getThemesPathFromProps() != null
    }

    fun getActiveThemeData(): ThemeDataClass {
        val themeName = getActiveTheme()
        return if (themeName.startsWith("assets:") && Application.context != null) {
            val imgName =
                themeName
                    .removePrefix("assets:theme_package_metadata_")
                    .removeSuffix(".binarypb")
            val image = Application.context?.let {
                try {
                    val inputStream = it.resources.openRawResource(
                        FileUtils.getResourceId(
                            it,
                            imgName,
                            "raw",
                            it.packageName
                        )
                    )
                    BitmapFactory.decodeStream(BufferedInputStream(inputStream))
                } catch (e: Exception) {
                    null
                }
            }
            ThemeDataClass(
                image,
                imgName.split("_").joinToString(" ") { it.capitalize(Locale.getDefault()) }, ""
            )
        } else if (themeName.isNotEmpty()) {
            val image = SuFile(Config.THEME_LOCATION, themeName)
            ThemeDataClass(
                image.decodeBitmap(),
                themeName,
                SuFile(Config.THEME_LOCATION, "$themeName.zip").absolutePath
            )
        } else {
            if (Application.context != null) {
                val isDark =
                    (Application.context!!.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val image = Application.context?.let {
                    val inputStream = it.resources.openRawResource(
                        FileUtils.getResourceId(
                            it,
                            if (isDark) "google_blue_dark" else "default_snapshot",
                            "raw",
                            it.packageName
                        )
                    )
                    BitmapFactory.decodeStream(BufferedInputStream(inputStream))
                }
                ThemeDataClass(
                    image,
                    "System auto",
                    "",
                    colorFilter = if (isDark) null else PorterDuffColorFilter(
                        getDeviceAccentColor(Application.context!!),
                        PorterDuff.Mode.MULTIPLY
                    )
                )
            } else ThemeDataClass(null, "", "")
        }
    }

    @JvmStatic
    fun getDeviceAccentColor(context: Context): Int {
        val value = TypedValue()
        val ctx = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
        ctx.theme.resolveAttribute(android.R.attr.colorAccent, value, true)
        return value.data
    }

    @SuppressLint("InflateParams")
    fun getThemeView(theme: ThemeDataClass, context: Context): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(context), 8.dp(context), 8.dp(context), 0)
            addView(LayoutInflater.from(context).inflate(R.layout.single_theme_item, null).apply {
                val themeName = findViewById<TextView>(R.id.theme_name)
                val themeIcon = findViewById<ImageView>(R.id.theme_image)
                val card: CardView = findViewById(R.id.card)
                val gradient: View? = try {
                    findViewById(R.id.gradient)
                } catch (e: Exception) {
                    null
                }

                val defaultImage = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_keyboard
                )!!.getBitmap()

                themeIcon.setImageBitmap(theme.image ?: defaultImage)
                themeIcon.colorFilter = theme.colorFilter

                val color = ColorUtils.dominantColor(themeIcon.drawable.getBitmap())
                val isDark = ColorUtils.isColorLight(color)

                if (gradient != null) {
                    val g = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(color, Color.TRANSPARENT)
                    )
                    gradient.background = g
                }

                card.setCardBackgroundColor(color)

                themeName.text =
                    theme.name.split("_").joinToString(" ") { s ->
                        s.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(
                                Locale.getDefault()
                            ) else it.toString()
                        }
                    }
                themeName.setTextColor(if (!isDark) Color.WHITE else Color.BLACK)
            })
            addView(View(context).apply {
                setHeight(8.dp(context))
            })
            addView(View(context).apply {
                setHeight(1.dp(context) / 2)
                setWidth(LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(context.getAttrColor(R.attr.colorOnPrimary))
                alpha = .6F
            })
        }
    }
}