import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StoreElement } from './store-element';

describe('StoreElement', () => {
  let component: StoreElement;
  let fixture: ComponentFixture<StoreElement>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StoreElement],
    }).compileComponents();

    fixture = TestBed.createComponent(StoreElement);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
