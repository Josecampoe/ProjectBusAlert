package com.busalert.wear.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripPhraseParserTest {

    private val parser = ConnectorTripPhraseParser(
        listOf("y voy a", "voy a", "voy al", "quiero ir a", "hasta", "going to", "and going to"),
    )

    @Test
    fun `separa origen y destino con el conector`() {
        assertEquals(
            TripPhrase("estoy en Fátima", "la Universidad Cooperativa"),
            parser.split("estoy en Fátima y voy a la Universidad Cooperativa"),
        )
    }

    @Test
    fun `no distingue mayúsculas y quita la puntuación de los bordes`() {
        assertEquals(
            TripPhrase("Estoy en el Parque Bolívar", "Udenar"),
            parser.split("Estoy en el Parque Bolívar, Y VOY A Udenar."),
        )
    }

    @Test
    fun `acepta voy al y desde hasta`() {
        assertEquals(TripPhrase("Fátima", "Hospital Departamental"), parser.split("Fátima voy al Hospital Departamental"))
        assertEquals(TripPhrase("desde Catambuco", "Unicentro"), parser.split("desde Catambuco hasta Unicentro"))
    }

    @Test
    fun `funciona en inglés`() {
        assertEquals(TripPhrase("I'm at Fátima", "Udenar"), parser.split("I'm at Fátima and going to Udenar"))
    }

    @Test
    fun `sin origen o sin destino no separa`() {
        assertNull(parser.split("voy a la Universidad Cooperativa"))
        assertNull(parser.split("estoy en Fátima y voy a"))
        assertNull(parser.split("Parque Bolívar"))
    }

    @Test
    fun `solo reconoce conectores como palabras completas`() {
        // "hastaluego" o "convoy a" no son conectores.
        assertNull(parser.split("Barrio Hastalandia"))
        assertNull(parser.split("Calle del convoy azul"))
    }

    @Test
    fun `sin conectores nunca separa`() {
        assertNull(ConnectorTripPhraseParser(emptyList()).split("estoy en Fátima y voy a Udenar"))
        assertNull(TripPhraseParser.NONE.split("estoy en Fátima y voy a Udenar"))
    }
}
