package java.awt.geom

import java.awt.Rectangle
import java.awt.Shape

/**
 * The geometry of Java, as a stand in for Android.
 *
 * Apache POI describes the position and the size of the shapes of a legacy
 * document with the geometry of Java. Android does not know it, so the app
 * carries the few types that the readers of POI need.
 *
 * The classes of this file are a part of the compatibility layer of the app,
 * the app itself does not use them. They follow the hierarchy of Java, so the
 * reader of POI finds the types it expects.
 */

/** The path of the outline of a shape of Java. */
interface PathIterator {
    fun getCurrentSegment(): Int

    fun currentSegment(coords: kotlin.DoubleArray): Int

    fun isDone(): Boolean

    fun next(): PathIterator

    fun getWindingRule(): Int

    /** A point of the path of Java. */
    class Double(
        @JvmField var x: kotlin.Double,
        @JvmField var y: kotlin.Double,
    ) {
        constructor() : this(0.0, 0.0)
    }
}

/** The transformation of the coordinates of Java. */
class AffineTransform @JvmOverloads constructor(
    @JvmField var m00: kotlin.Double = 1.0,
    @JvmField var m01: kotlin.Double = 0.0,
    @JvmField var m02: kotlin.Double = 0.0,
    @JvmField var m10: kotlin.Double = 0.0,
    @JvmField var m11: kotlin.Double = 1.0,
    @JvmField var m12: kotlin.Double = 0.0,
) {
    constructor(transform: AffineTransform) : this(
        transform.m00,
        transform.m01,
        transform.m02,
        transform.m10,
        transform.m11,
        transform.m12,
    )

    constructor(
        m00: Float,
        m01: Float,
        m02: Float,
        m10: Float,
        m11: Float,
        m12: Float,
    ) : this(
        m00.toDouble(),
        m01.toDouble(),
        m02.toDouble(),
        m10.toDouble(),
        m11.toDouble(),
        m12.toDouble(),
    )

    fun getScaleX(): kotlin.Double = m00

    fun getScaleY(): kotlin.Double = m11

    fun getShearX(): kotlin.Double = m01

    fun getShearY(): kotlin.Double = m10

    fun getTranslateX(): kotlin.Double = m02

    fun getTranslateY(): kotlin.Double = m12

    fun translate(tx: kotlin.Double, ty: kotlin.Double) {
        m02 += tx
        m12 += ty
    }

    fun scale(sx: kotlin.Double, sy: kotlin.Double) {
        m00 *= sx
        m01 *= sx
        m02 *= sx
        m10 *= sy
        m11 *= sy
        m12 *= sy
    }

    fun concatenate(transform: AffineTransform) {
        val a00 = m00 * transform.m00 + m01 * transform.m10
        val a01 = m00 * transform.m01 + m01 * transform.m11
        val a02 = m00 * transform.m02 + m01 * transform.m12 + m02
        val a10 = m10 * transform.m00 + m11 * transform.m10
        val a11 = m10 * transform.m01 + m11 * transform.m11
        val a12 = m10 * transform.m02 + m11 * transform.m12 + m12
        m00 = a00
        m01 = a01
        m02 = a02
        m10 = a10
        m11 = a11
        m12 = a12
    }

    fun transform(source: Point2D, destination: Point2D) {
        val x = source.getX()
        val y = source.getY()
        destination.setLocation(m00 * x + m01 * y + m02, m10 * x + m11 * y + m12)
    }

    fun transform(points: kotlin.DoubleArray, offset: Int, count: Int) {
        for (index in 0 until count step 2) {
            val x = points[offset + index]
            val y = points[offset + index + 1]
            points[offset + index] = m00 * x + m01 * y + m02
            points[offset + index + 1] = m10 * x + m11 * y + m12
        }
    }

    fun inverse(): AffineTransform {
        val determinant = m00 * m11 - m01 * m10
        if (determinant == 0.0) return AffineTransform()
        val inverse = 1.0 / determinant
        return AffineTransform(
            m11 * inverse,
            -m01 * inverse,
            (m01 * m12 - m02 * m11) * inverse,
            -m10 * inverse,
            m00 * inverse,
            (m02 * m10 - m00 * m12) * inverse,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val transform = other as? AffineTransform ?: return false
        return m00 == transform.m00 && m01 == transform.m01 && m02 == transform.m02 &&
            m10 == transform.m10 && m11 == transform.m11 && m12 == transform.m12
    }

    override fun hashCode(): Int {
        var result = m00.hashCode()
        result = 31 * result + m01.hashCode()
        result = 31 * result + m02.hashCode()
        result = 31 * result + m10.hashCode()
        result = 31 * result + m11.hashCode()
        result = 31 * result + m12.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        fun getTranslateInstance(tx: kotlin.Double, ty: kotlin.Double): AffineTransform =
            AffineTransform(1.0, 0.0, tx, 0.0, 1.0, ty)

        @JvmStatic
        fun getScaleInstance(sx: kotlin.Double, sy: kotlin.Double): AffineTransform =
            AffineTransform(sx, 0.0, 0.0, 0.0, sy, 0.0)
    }
}

/** The shape of Java that is described by a rectangle. */
abstract class RectangularShape : Shape {

    abstract fun getX(): kotlin.Double

    abstract fun getY(): kotlin.Double

    abstract fun getWidth(): kotlin.Double

    abstract fun getHeight(): kotlin.Double

    abstract fun setBounds(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double)

    abstract fun setBounds(rect: Rectangle2D)

    fun getMinX(): kotlin.Double = getX()

    fun getMinY(): kotlin.Double = getY()

    fun getMaxX(): kotlin.Double = getX() + getWidth()

    fun getMaxY(): kotlin.Double = getY() + getHeight()

    fun getCenterX(): kotlin.Double = getX() + getWidth() / 2

    fun getCenterY(): kotlin.Double = getY() + getHeight() / 2

    override fun getBounds(): Rectangle = Rectangle(
        getX().toInt(),
        getY().toInt(),
        getWidth().toInt(),
        getHeight().toInt(),
    )

    override fun contains(x: kotlin.Double, y: kotlin.Double): Boolean =
        x >= getMinX() && x <= getMaxX() && y >= getMinY() && y <= getMaxY()

    override fun contains(point: Point2D): Boolean = contains(point.getX(), point.getY())

    override fun contains(rect: Rectangle2D): Boolean =
        rect.getMinX() >= getMinX() && rect.getMaxX() <= getMaxX() &&
            rect.getMinY() >= getMinY() && rect.getMaxY() <= getMaxY()

    override fun intersects(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double): Boolean =
        x <= getMaxX() && x + width >= getMinX() && y <= getMaxY() && y + height >= getMinY()

    override fun intersects(rect: Rectangle2D): Boolean = intersects(
        rect.getMinX(),
        rect.getMinY(),
        rect.getWidth(),
        rect.getHeight(),
    )

    override fun getPathIterator(at: AffineTransform?): PathIterator =
        throw UnsupportedOperationException("the path of the shape is not needed")

    override fun createTransformedShape(tx: AffineTransform): Shape =
        throw UnsupportedOperationException("the shape is not needed")
}

/** The rectangle of Java, the position of the shapes of a document is described with it. */
abstract class Rectangle2D : RectangularShape() {

    abstract fun setRect(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double)

    /** The rectangle of Java with double precision. */
    class Double(
        @JvmField var x: kotlin.Double,
        @JvmField var y: kotlin.Double,
        @JvmField var width: kotlin.Double,
        @JvmField var height: kotlin.Double,
    ) : Rectangle2D() {

        constructor() : this(0.0, 0.0, 0.0, 0.0)

        constructor(rect: Rectangle2D?) : this(
            rect?.getX() ?: 0.0,
            rect?.getY() ?: 0.0,
            rect?.getWidth() ?: 0.0,
            rect?.getHeight() ?: 0.0,
        )

        override fun getX(): kotlin.Double = x

        override fun getY(): kotlin.Double = y

        override fun getWidth(): kotlin.Double = width

        override fun getHeight(): kotlin.Double = height

        override fun setRect(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double) {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
        }

        override fun setBounds(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double) {
            setRect(x, y, width, height)
        }

        override fun setBounds(rect: Rectangle2D) = setRect(
            rect.getX(),
            rect.getY(),
            rect.getWidth(),
            rect.getHeight(),
        )
    }
}

/** The point of Java. */
abstract class Point2D {

    abstract fun getX(): kotlin.Double

    abstract fun getY(): kotlin.Double

    abstract fun setLocation(x: kotlin.Double, y: kotlin.Double)

    fun setLocation(point: Point2D) = setLocation(point.getX(), point.getY())

    fun getDistance(x: kotlin.Double, y: kotlin.Double): kotlin.Double {
        val dx = getX() - x
        val dy = getY() - y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun getDistance(point: Point2D): kotlin.Double = getDistance(point.getX(), point.getY())

    /** The point of Java with double precision. */
    class Double(
        @JvmField var x: kotlin.Double,
        @JvmField var y: kotlin.Double,
    ) : Point2D() {

        constructor() : this(0.0, 0.0)

        override fun getX(): kotlin.Double = x

        override fun getY(): kotlin.Double = y

        override fun setLocation(x: kotlin.Double, y: kotlin.Double) {
            this.x = x
            this.y = y
        }
    }
}

/** The path of a shape of Java. */
abstract class Path2D : Shape {

    abstract fun moveTo(x: kotlin.Double, y: kotlin.Double)

    abstract fun lineTo(x: kotlin.Double, y: kotlin.Double)

    abstract fun curveTo(x1: kotlin.Double, y1: kotlin.Double, x2: kotlin.Double, y2: kotlin.Double, x3: kotlin.Double, y3: kotlin.Double)

    abstract fun quadTo(x1: kotlin.Double, y1: kotlin.Double, x2: kotlin.Double, y2: kotlin.Double)

    abstract fun closePath()

    abstract fun reset()

    abstract override fun getPathIterator(at: AffineTransform?): PathIterator

    override fun getBounds(): Rectangle = Rectangle()

    override fun contains(x: kotlin.Double, y: kotlin.Double): Boolean = false

    override fun contains(point: Point2D): Boolean = false

    override fun contains(rect: Rectangle2D): Boolean = false

    override fun intersects(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double): Boolean = false

    override fun intersects(rect: Rectangle2D): Boolean = false

    override fun createTransformedShape(tx: AffineTransform): Shape =
        throw UnsupportedOperationException("the shape is not needed")

    /** The path of Java with double precision. */
    class Double(
        @JvmField var x: kotlin.Double = 0.0,
        @JvmField var y: kotlin.Double = 0.0,
    ) : Path2D() {

        override fun moveTo(x: kotlin.Double, y: kotlin.Double) {
            this.x = x
            this.y = y
        }

        override fun lineTo(x: kotlin.Double, y: kotlin.Double) {
            this.x = x
            this.y = y
        }

        override fun curveTo(
            x1: kotlin.Double,
            y1: kotlin.Double,
            x2: kotlin.Double,
            y2: kotlin.Double,
            x3: kotlin.Double,
            y3: kotlin.Double,
        ) {
            this.x = x3
            this.y = y3
        }

        override fun quadTo(x1: kotlin.Double, y1: kotlin.Double, x2: kotlin.Double, y2: kotlin.Double) {
            this.x = x2
            this.y = y2
        }

        override fun closePath() = Unit

        override fun reset() {
            x = 0.0
            y = 0.0
        }

        override fun getPathIterator(at: AffineTransform?): PathIterator =
            throw UnsupportedOperationException("the path is not needed")
    }
}

/** The arc of a shape of Java. */
abstract class Arc2D : Shape {

    abstract fun getCenterX(): kotlin.Double

    abstract fun getCenterY(): kotlin.Double

    abstract fun getStartAngle(): kotlin.Double

    abstract fun getExtent(): kotlin.Double

    override fun getBounds(): Rectangle = Rectangle()

    override fun contains(x: kotlin.Double, y: kotlin.Double): Boolean = false

    override fun contains(point: Point2D): Boolean = false

    override fun contains(rect: Rectangle2D): Boolean = false

    override fun intersects(x: kotlin.Double, y: kotlin.Double, width: kotlin.Double, height: kotlin.Double): Boolean = false

    override fun intersects(rect: Rectangle2D): Boolean = false

    override fun getPathIterator(at: AffineTransform?): PathIterator =
        throw UnsupportedOperationException("the path is not needed")

    override fun createTransformedShape(tx: AffineTransform): Shape =
        throw UnsupportedOperationException("the shape is not needed")

    /** The arc of Java with double precision. */
    class Double(
        @JvmField var x: kotlin.Double,
        @JvmField var y: kotlin.Double,
        @JvmField var width: kotlin.Double,
        @JvmField var height: kotlin.Double,
        @JvmField var start: kotlin.Double,
        @JvmField var extent: kotlin.Double,
    ) : Arc2D() {

        constructor() : this(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        override fun getCenterX(): kotlin.Double = x + width / 2

        override fun getCenterY(): kotlin.Double = y + height / 2

        override fun getStartAngle(): kotlin.Double = start

        override fun getExtent(): kotlin.Double = extent
    }
}
