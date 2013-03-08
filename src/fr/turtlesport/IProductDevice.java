package fr.turtlesport;

/**
 * Carat&eacute;rise un produit Garmin ou autres.
 * 
 * @author Denis Apparicio
 * 
 */
public interface IProductDevice {

  String displayName();

  String id();

  String softwareVersion();
  
}