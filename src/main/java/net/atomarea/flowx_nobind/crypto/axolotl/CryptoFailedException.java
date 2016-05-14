package net.atomarea.flowx_nobind.crypto.axolotl;

public class CryptoFailedException extends Exception {
	public CryptoFailedException(Exception e){
		super(e);
	}
}
