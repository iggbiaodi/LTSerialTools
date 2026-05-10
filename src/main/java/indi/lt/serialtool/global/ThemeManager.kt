package indi.lt.serialtool.global

import github.nonoas.jfx.flat.ui.theme.*

/**
 * @author Nonoas
 * @date 2025/9/18
 * @since 1.0.0
 */
object ThemeManager {
    @JvmStatic
    val all: List<Theme>
        get() = listOf(
            Claude(),
            CupertinoDark(),
            CupertinoLight(),
            Dracula(),
            NordDark(),
            NordLight(),
            PrimerDark(),
            PrimerLight()
        )
}
