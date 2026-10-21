package br.com.cronomed.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CsvEscapeTest {

    @Test
    void neutralizesFormulaInjection() {
        assertEquals("'=SUM(A1)", DashboardService.csv("=SUM(A1)"));
        assertEquals("'@cmd", DashboardService.csv("@cmd"));
    }

    @Test
    void quotesSpecialCharacters() {
        assertEquals("\"a,b\"", DashboardService.csv("a,b"));
        assertEquals("\"diz \"\"oi\"\"\"", DashboardService.csv("diz \"oi\""));
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", DashboardService.csv(null));
    }
}
