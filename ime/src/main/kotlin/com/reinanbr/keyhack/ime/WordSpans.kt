package com.reinanbr.keyhack.ime

/** Word boundaries before the cursor, for deleting whole words (Delete drag, Delete held). */
internal object WordSpans {
    /**
     * How many characters to delete to remove the last 1, 2, ... [max] words
     * of [before]: "olá mundo  " -> [7, 11]. Spaces after a word go with it;
     * a run of punctuation or emoji counts as one word.
     */
    fun deleteLengths(before: CharSequence, max: Int): List<Int> {
        val out = ArrayList<Int>()
        var i = before.length
        while (out.size < max && i > 0) {
            while (i > 0 && before[i - 1].isWhitespace()) i--
            if (i > 0 && isWordChar(before[i - 1])) {
                while (i > 0 && isWordChar(before[i - 1])) i--
            } else {
                while (i > 0 && !isWordChar(before[i - 1]) && !before[i - 1].isWhitespace()) i--
            }
            out += before.length - i
        }
        return out
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '\'' || c == '’' || c == '-'
}
