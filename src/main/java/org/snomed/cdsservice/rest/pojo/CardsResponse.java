package org.snomed.cdsservice.rest.pojo;

import org.snomed.cdsservice.model.CDSCard;

import java.util.List;

public class CardsResponse {

	private final List<CDSCard> cards;

	public CardsResponse(List<CDSCard> cards) {
		this.cards = cards;
	}

	public List<CDSCard> getCards() {
		return cards;
	}
}
