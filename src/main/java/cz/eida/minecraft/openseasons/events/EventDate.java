/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

/**
 * Date in an event definition ({@code times.event-start.date}). Three forms:
 * {@code dd/mm/yyyy} (once), {@code dd/mm} (every year) and only a day {@code 5} (every month);
 * with {@code american-date-format} the month before the day.
 *
 * @param month 0 = every month
 * @param year  0 = every year
 */
public record EventDate(int day, int month, int year) {

    /** how often the date repeats */
    public enum Repeat {
        /** only a day of the month */
        MONTHLY,
        /** day and month */
        YEARLY,
        /** day, month and year */
        ONCE
    }

    public EventDate {
        if (day < 1) throw new IllegalArgumentException("day must be at least 1, not " + day);
        if (month < 0 || year < 0) throw new IllegalArgumentException("invalid date " + day + "/" + month + "/" + year);
        if (month == 0 && year != 0) throw new IllegalArgumentException("year without a month");
    }

    public Repeat repeat() {
        if (month == 0) return Repeat.MONTHLY;
        return year == 0 ? Repeat.YEARLY : Repeat.ONCE;
    }

    /**
     * {@code 25/12}, {@code 1/1/450}, {@code 5}; s {@code american} {@code 12/25}.
     *
     * @throws IllegalArgumentException with a message for administrators
     */
    public static EventDate parse(String text, boolean american) {
        String t = text == null ? "" : text.trim();
        String[] parts = t.split("/", -1);
        if (t.isEmpty() || parts.length > 3) {
            throw new IllegalArgumentException("date '" + text + "' must be " + (american ? "mm/dd/yyyy, mm/dd" : "dd/mm/yyyy, dd/mm")
                    + " or just a day");
        }
        int[] n = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                n[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("date '" + text + "': '" + parts[i] + "' is not a number");
            }
            if (n[i] < 1) throw new IllegalArgumentException("date '" + text + "': numbers must be at least 1");
        }
        return switch (n.length) {
            case 1 -> new EventDate(n[0], 0, 0);
            case 2 -> american ? new EventDate(n[1], n[0], 0) : new EventDate(n[0], n[1], 0);
            default -> american ? new EventDate(n[1], n[0], n[2]) : new EventDate(n[0], n[1], n[2]);
        };
    }

    public String format(boolean american) {
        if (month == 0) return Integer.toString(day);
        String dm = american ? month + "/" + day : day + "/" + month;
        return year == 0 ? dm : dm + "/" + year;
    }
}
