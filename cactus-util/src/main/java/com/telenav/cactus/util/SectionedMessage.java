package com.telenav.cactus.util;

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.telenav.cactus.util.SectionedMessage.MessageSection;
import java.util.Collection;

/**
 * A structured message which may have sections with headings; the main
 * implementation is CommitMessage in the maven plugin, and this interface
 * allows it to be passed to libraries that don't and shouldn't know about that
 * type.
 *
 * @author Tim Boudreau
 */
public interface SectionedMessage<S extends MessageSection<S>>
{

    /**
     * Append a paragraph to the main text.
     *
     * @param text The text
     * @return this
     */
    SectionedMessage paragraph(CharSequence text);

    /**
     * Create a new MessageSection. close() needs to be called on the section
     * for it to be added.
     *
     * @param heading The heading text
     * @return A MessageSection
     */
    S section(String heading);

    /**
     * Create a section, closing it after the passed consumer runs.
     *
     * @param heading A heading
     * @param run Something that will use the section
     * @return this
     * @throws Exception if the consumer throws something
     */
    default SectionedMessage section(String heading, ThrowingConsumer<S> run)
            throws Exception
    {
        try ( S s = section(heading))
        {
            run.accept(s);
        }
        return this;
    }

    /**
     * A titled section of a message
     *
     * @param <S> The type of this section
     */
    public interface MessageSection<S extends MessageSection<S>> extends
            AutoCloseable
    {
        /**
         * Add a paragraph below the
         *
         * @param text The text
         * @return this
         */
        S paragraph(CharSequence text);

        /**
         * Add a bullet point to this section
         *
         * @param depth The indent depth
         * @param text The text
         * @return this
         */
        S bulletPoint(int depth, Object text);

        @SuppressWarnings("unchecked")
        default S cast()
        {
            return (S) this;
        }

        default S bulletPoints(int depth, Collection<?> items)
        {
            items.forEach(i -> this.bulletPoint(depth, i));
            return cast();
        }

        default S bulletPoints(Collection<?> items)
        {
            items.forEach(i -> this.bulletPoint(i));
            return cast();
        }

        default S bulletPoint(Object text)
        {
            return bulletPoint(1, text);
        }

        @Override
        void close();
    }
}
