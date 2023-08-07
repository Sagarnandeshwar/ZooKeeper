/**
 * Utility enum values to print ANSI formatted background coloured output to console.
 * @link <a href="https://en.wikipedia.org/wiki/ANSI_escape_code#3-bit_and_4-bit">Wikipedia</a>
 */
@SuppressWarnings("unused")
public enum Col {
	// 16 bit colours
	BLACK(30, 40),
	RED(31, 41),
	GREEN(32, 42),
	YELLOW(33, 43),
	BLUE(34, 44),
	MAGENTA(35, 45),
	CYAN(36, 46),
	WHITE(37, 47),
	GRAY(90, 100),
	B_RED(91, 101),
	B_GREEN(92, 102),
	B_YELLOW(93, 103),
	B_BLUE(94, 104),
	B_MAGENTA(95, 105),
	B_CYAN(96, 106),
	B_WHITE(97, 107),
	RESET(0, 0);

	final int fgCode;
	final String fg;
	final int bgCode;
	final String bg;

	Col(int fgCode, int bgCode) {
		this.fgCode = fgCode;
		this.fg = String.format("\u001B[%dm", fgCode);
		this.bgCode = bgCode;
		this.bg = String.format("\u001B[%dm", bgCode);
	}

	@Override
	public String toString() {
		return this.fg;
	}

	/** Colour the foreground of some text. */
	public String fg(String msg) {
		return fg + msg + RESET.fg;
	}

	/** Colour the background of some text.  */
	public String bg(String msg) {
		return bg + msg + RESET.fg;
	}

	/** Colour some text with the given background and foreground. */
	public static String mix(Col fg, Col bg, String msg) {
		return fg.fg + bg.bg + msg + RESET.fg;
	}

}
