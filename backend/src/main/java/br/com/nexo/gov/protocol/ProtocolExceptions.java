package br.com.nexo.gov.protocol;

public final class ProtocolExceptions {
    private ProtocolExceptions() {}
    public static class NotFound extends RuntimeException { public NotFound() { super("Registro não encontrado."); } }
    public static class InvalidTransition extends RuntimeException { public InvalidTransition(String message) { super(message); } }
    public static class ForbiddenAction extends RuntimeException { public ForbiddenAction() { super("Ação não autorizada."); } }
}
